package io.slychat.messenger.services.messaging

import io.slychat.messenger.core.currentTimestamp
import io.slychat.messenger.core.persistence.ConversationId
import io.slychat.messenger.core.persistence.ExpiringMessage
import io.slychat.messenger.services.MessageUpdateEvent
import nl.komponents.kovenant.ui.successUi
import org.slf4j.LoggerFactory
import rx.Scheduler
import rx.Subscription
import java.util.*
import java.util.concurrent.TimeUnit

class MessageExpirationWatcherImpl(
    private val scheduler: Scheduler,
    private val rxTimerFactory: RxTimerFactory,
    private val messageService: MessageService
) : MessageExpirationWatcher {
    private val log = LoggerFactory.getLogger(javaClass)

    private val expiringMessages = ExpiringMessages()

    private var currentTimer: Subscription? = null
    private var nextExpirationTime = 0L

    init {
        messageService.messageUpdates.subscribe { onMessageUpdate(it) }
    }

    override fun init() {
        messageService.getMessagesAwaitingExpiration() successUi {
            processInitial(it)
        }
    }

    private fun processInitial(expiringMessages: List<ExpiringMessage>) {
        val toDestroy = MessageListMap()
        val toAdd = ArrayList<ExpiringMessages.ExpiringEntry>()

        val currentTime = currentTimestamp()

        expiringMessages.forEach { expiringMessage ->
            val conversationId = expiringMessage.conversationId
            val messageId = expiringMessage.messageId

            if (expiringMessage.expiresAt <= currentTime)
                toDestroy[conversationId].add(messageId)
            else {
                val entry = ExpiringMessages.ExpiringEntry(conversationId, messageId, expiringMessage.expiresAt)
                toAdd.add(entry)
            }
        }

        this.expiringMessages.addAll(toAdd)
        updateTimer()

        if (toDestroy.isNotEmpty()) {
            messageService.expireMessages(toDestroy.toMap()) fail {
                log.error("Unable to destroy messages: {}", it.message, it)
            }
        }
    }

    override fun shutdown() {
        currentTimer?.unsubscribe()
    }

    private fun onMessageUpdate(event: MessageUpdateEvent) = when (event) {
        is MessageUpdateEvent.Expiring -> onMessageExpiring(event)
        is MessageUpdateEvent.Expired -> onMessageExpired(event)
        else -> {}
    }

    //this is here for consistency between devices; if I start a countdown on one device, and then one on another,
    //the first one that finishes should cause the message to be destroyed on both (assuming both are still online)
    private fun onMessageExpired(event: MessageUpdateEvent.Expired) {
        log.debug("Message {}/{} has expired", event.conversationId, event.messageId)

        if (expiringMessages.remove(event.conversationId, event.messageId))
            updateTimer()
    }

    private fun onMessageExpiring(event: MessageUpdateEvent.Expiring) {
        log.debug("Message {}/{} is expiring at {}", event.conversationId, event.messageId, event.expiresAt)

        val entry = ExpiringMessages.ExpiringEntry(event.conversationId, event.messageId, event.expiresAt)
        expiringMessages.add(entry)

        updateTimer()
    }

    private fun updateTimer() {
        val prevExpirationTime = this.nextExpirationTime
        nextExpirationTime = expiringMessages.nextExpiration()

        if (nextExpirationTime == prevExpirationTime)
            return

        currentTimer?.unsubscribe()

        if (nextExpirationTime == 0L)
            return

        val ttl = Math.max(nextExpirationTime - currentTimestamp(), 0)

        currentTimer = rxTimerFactory.createTimer(ttl, TimeUnit.MILLISECONDS).observeOn(scheduler).subscribe { onTimer() }
    }

    private fun onTimer() {
        currentTimer = null
        nextExpirationTime = 0

        //delete any expired messages
        val expired = expiringMessages.removeExpired(currentTimestamp())

        val m = HashMap<ConversationId, MutableList<String>>()

        expired.forEach {
            val conversationId = it.conversationId

            val messageIds = if (conversationId !in m) {
                val messageIds = ArrayList<String>()
                m[conversationId] = messageIds
                messageIds
            }
            else
                m[conversationId]!!

            messageIds.add(it.messageId)
        }

        messageService.expireMessages(m) fail {
            log.error("Failed to destroy messages: {}", it.message, it)
        }

        //then restart the timer for the next message
        updateTimer()
    }
}