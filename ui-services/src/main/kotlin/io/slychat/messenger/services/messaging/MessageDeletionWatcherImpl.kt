package io.slychat.messenger.services.messaging

import io.slychat.messenger.services.MessageUpdateEvent
import org.slf4j.LoggerFactory
import rx.Observable
import rx.subscriptions.CompositeSubscription

class MessageDeletionWatcherImpl(
    messageUpdates: Observable<MessageUpdateEvent>,
    private val messengerService: MessengerService
) : MessageDeletionWatcher {
    private val log = LoggerFactory.getLogger(javaClass)

    private var subscriptions = CompositeSubscription()

    init {
        subscriptions.add(messageUpdates.subscribe { onMessageUpdate(it) })
    }

    private fun onMessageUpdate(event: MessageUpdateEvent) {
        when (event) {
            is MessageUpdateEvent.Deleted -> if (!event.fromSync) onDeleted(event)
            is MessageUpdateEvent.DeletedAll -> if (!event.fromSync) onDeletedAll(event)
            else -> {}
        }
    }

    private fun onDeletedAll(event: MessageUpdateEvent.DeletedAll) {
        messengerService.broadcastDeletedAll(event.conversationId, event.lastMessageTimestamp) fail {
            log.error("Unable to broadcast deleted all messages for {}: {}", event.conversationId, it.message, it)
        }
    }

    private fun onDeleted(event: MessageUpdateEvent.Deleted) {
        messengerService.broadcastDeleted(event.conversationId, event.messageIds) fail {
            log.error("Unable to broadcast deleted messages for {}: {}", event.conversationId, it.message, it)
        }
    }

    override fun init() {
    }

    override fun shutdown() {
        subscriptions.clear()
    }
}