package io.slychat.messenger.services.messaging

import io.slychat.messenger.core.persistence.ConversationId
import io.slychat.messenger.core.persistence.ConversationMessageInfo
import io.slychat.messenger.core.persistence.MessagePersistenceManager
import io.slychat.messenger.services.MessageUpdateEvent
import io.slychat.messenger.services.mapUi
import nl.komponents.kovenant.Promise
import nl.komponents.kovenant.ui.successUi
import rx.Observable
import rx.subjects.PublishSubject

class MessageServiceImpl(
    private val messagePersistenceManager: MessagePersistenceManager
) : MessageService {
    private val newMessagesSubject = PublishSubject.create<ConversationMessage>()
    override val newMessages: Observable<ConversationMessage>
        get() = newMessagesSubject

    private val messageUpdatesSubject = PublishSubject.create<MessageUpdateEvent>()
    override val messageUpdates: Observable<MessageUpdateEvent>
        get() = messageUpdatesSubject

    override fun markMessageAsDelivered(conversationId: ConversationId, messageId: String, timestamp: Long): Promise<ConversationMessageInfo?, Exception> {
        return messagePersistenceManager.markMessageAsDelivered(conversationId, messageId, timestamp) successUi {
            if (it != null) {
                val update = MessageUpdateEvent.Delivered(conversationId, messageId, timestamp)
                messageUpdatesSubject.onNext(update)
            }
        }
    }

    override fun addNewMessage(conversationId: ConversationId, conversationMessageInfo: ConversationMessageInfo): Promise<Unit, Exception> {
        return messagePersistenceManager.addMessage(conversationId, conversationMessageInfo) mapUi {
            val conversationMessage = when (conversationId) {
                is ConversationId.User -> ConversationMessage.Single(conversationId.id, conversationMessageInfo.info)
                is ConversationId.Group -> ConversationMessage.Group(conversationId.id, conversationMessageInfo.speaker, conversationMessageInfo.info)
            }

            newMessagesSubject.onNext(conversationMessage)
        }
    }

    override fun markConversationAsRead(conversationId: ConversationId): Promise<Unit, Exception> {
        return messagePersistenceManager.markConversationAsRead(conversationId)
    }
}