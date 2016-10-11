package io.slychat.messenger.services

import io.slychat.messenger.core.persistence.ConversationId
import io.slychat.messenger.services.messaging.ConversationMessage
import io.slychat.messenger.services.messaging.MessageService
import io.slychat.messenger.services.messaging.MessengerService
import org.slf4j.LoggerFactory
import rx.subscriptions.CompositeSubscription

class MessageReadWatcherImpl(
    messageService: MessageService,
    private val messengerService: MessengerService
) : MessageReadWatcher {
    private val log = LoggerFactory.getLogger(javaClass)

    private var subscriptions = CompositeSubscription()

    init {
        subscriptions.add(messageService.messageUpdates.ofType(MessageUpdateEvent.Read::class.java).subscribe { onMessagesRead(it) })

        subscriptions.add(messageService.newMessages.filter {
            val info = it.conversationMessageInfo.info
            info.isSent == false && info.isRead == true
        }.subscribe { onNewReadMessage(it) })
    }

    private fun onNewReadMessage(conversationMessage: ConversationMessage) {
        val messageIds = listOf(conversationMessage.conversationMessageInfo.info.id)

        broadcastMessagesRead(conversationMessage.conversationId, messageIds)
    }

    private fun onMessagesRead(event: MessageUpdateEvent.Read) {
        if (event.fromSync)
            return

        broadcastMessagesRead(event.conversationId, event.messageIds)
    }

    private fun broadcastMessagesRead(conversationId: ConversationId, messageIds: List<String>) {
        messengerService.broadcastMessagesRead(conversationId, messageIds) fail {
            log.error("Unable to broadcast read messages for {}: {}", conversationId, it.message, it)
        }
    }

    override fun init() {
    }

    override fun shutdown() {
        subscriptions.clear()
    }
}