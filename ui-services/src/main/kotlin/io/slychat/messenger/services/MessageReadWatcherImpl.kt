package io.slychat.messenger.services

import io.slychat.messenger.services.messaging.MessageService
import io.slychat.messenger.services.messaging.MessengerService
import org.slf4j.LoggerFactory
import rx.Subscription

class MessageReadWatcherImpl(
    messageService: MessageService,
    private val messengerService: MessengerService
) : MessageReadWatcher {
    private val log = LoggerFactory.getLogger(javaClass)

    private var subscription: Subscription? = null

    init {
        messageService.messageUpdates.ofType(MessageUpdateEvent.Read::class.java).subscribe { onMessagesRead(it) }
    }

    private fun onMessagesRead(event: MessageUpdateEvent.Read) {
        if (event.fromSync)
            return

        messengerService.broadcastMessagesRead(event.conversationId, event.messageIds) fail {
            log.error("Unable to broadcast read messages for {}: {}", event.conversationId, it.message, it)
        }
    }

    override fun init() {
    }

    override fun shutdown() {
        subscription?.unsubscribe()
        subscription = null
    }
}