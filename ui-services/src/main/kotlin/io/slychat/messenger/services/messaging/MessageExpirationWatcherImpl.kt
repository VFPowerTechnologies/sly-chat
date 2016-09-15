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
    private val messageService: MessageService,
    private val messengerService: MessengerService
) : MessageExpirationWatcher {
    private val log = LoggerFactory.getLogger(javaClass)

    private val expiringMessages = ExpiringMessages()

    private var currentTimer: Subscription? = null
    private var nextExpirationTime = 0L

    init {
        messageService.messageUpdates.subscribe { onMessageUpdate(it) }
        messageService.newMessages.filter { it.info.isSent && it.info.ttl > 0 }.subscribe { onSelfExpiringMessage(it) }
    }

    private fun onSelfExpiringMessage(conversationMessage: ConversationMessage) {
        val conversationId = conversationMessage.conversationId
        val messageId = conversationMessage.info.id

        messageService.startMessageExpiration(conversationId, messageId) fail {
            log.error("Error attempting to expire a self message for {}/{}: {}", conversationId, messageId, it.message, it)
        }
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
            messageService.expireMessages(toDestroy.toMap(), false) fail {
                log.error("Unable to destroy messages: {}", it.message, it)
            }
        }
    }

    override fun shutdown() {
        currentTimer?.unsubscribe()
    }

    private fun onMessageUpdate(event: MessageUpdateEvent) {
        return when (event) {
            is MessageUpdateEvent.Expiring -> onMessageExpiring(event)
            is MessageUpdateEvent.Expired -> onMessageExpired(event)
            is MessageUpdateEvent.Deleted -> onMessagesDeleted(event)
            is MessageUpdateEvent.DeletedAll -> onAllMessagesDelete(event)
            else -> {}
        }
    }

    private fun onAllMessagesDelete(event: MessageUpdateEvent.DeletedAll) {
        if (expiringMessages.removeAll(event.conversationId))
            updateTimer()
    }

    private fun onMessagesDeleted(event: MessageUpdateEvent.Deleted) {
        event.messageIds.forEach {
            expiringMessages.remove(event.conversationId, it)
        }

        updateTimer()
    }

    //this is here for consistency between devices; if I start a countdown on one device, and then one on another,
    //the first one that finishes should cause the message to be destroyed on both (assuming both are still online)
    private fun onMessageExpired(event: MessageUpdateEvent.Expired) {
        val conversationId = event.conversationId
        val messageId = event.messageId

        log.debug("Message {}/{} has expired", conversationId, messageId)

        if (expiringMessages.remove(conversationId, messageId))
            updateTimer()

        if (!event.fromSync) {
            messengerService.broadcastMessageExpired(conversationId, messageId) fail {
                log.error("Failed to broadcast expired message {}/{}: {}", conversationId, messageId, it.message, it)
            }
        }
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

        messageService.expireMessages(m, false) fail {
            log.error("Failed to destroy messages: {}", it.message, it)
        }

        //then restart the timer for the next message
        updateTimer()
    }
}