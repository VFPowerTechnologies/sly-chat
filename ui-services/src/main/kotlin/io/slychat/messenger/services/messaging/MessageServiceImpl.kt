package io.slychat.messenger.services.messaging

import io.slychat.messenger.core.currentTimestamp
import io.slychat.messenger.core.persistence.*
import io.slychat.messenger.services.MessageUpdateEvent
import io.slychat.messenger.services.mapUi
import nl.komponents.kovenant.Promise
import nl.komponents.kovenant.functional.bind
import nl.komponents.kovenant.ui.successUi
import org.slf4j.LoggerFactory
import rx.Observable
import rx.subjects.PublishSubject

class MessageServiceImpl(
    private val messagePersistenceManager: MessagePersistenceManager
) : MessageService {
    private val log = LoggerFactory.getLogger(javaClass)

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

    override fun addMessage(conversationId: ConversationId, conversationMessageInfo: ConversationMessageInfo): Promise<Unit, Exception> {
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

    override fun deleteMessages(conversationId: ConversationId, messageIds: Collection<String>): Promise<Unit, Exception> {
        return messagePersistenceManager.deleteMessages(conversationId, messageIds)
    }

    override fun deleteAllMessages(conversationId: ConversationId): Promise<Unit, Exception> {
        return messagePersistenceManager.deleteAllMessages(conversationId)
    }

    override fun getAllUserConversations(): Promise<List<UserConversation>, Exception> {
        return messagePersistenceManager.getAllUserConversations()
    }

    override fun getAllGroupConversations(): Promise<List<GroupConversation>, Exception> {
        return messagePersistenceManager.getAllGroupConversations()
    }

    override fun getLastMessages(conversationId: ConversationId, startingAt: Int, count: Int): Promise<List<ConversationMessageInfo>, Exception> {
        return messagePersistenceManager.getLastMessages(conversationId, startingAt, count)
    }

    private fun startMessageExpiration(conversationId: ConversationId, messageId: String, conversationMessageInfo: ConversationMessageInfo?): Promise<Unit, Exception> {
        if (conversationMessageInfo == null) {
            log.warn("Invalid message id {} for conversation {}", messageId, conversationId)
            return Promise.ofSuccess(Unit)
        }

        val expiresAt = currentTimestamp() + conversationMessageInfo.info.ttl

        return messagePersistenceManager.setExpiration(conversationId, messageId, expiresAt) successUi {
            messageUpdatesSubject.onNext(MessageUpdateEvent.Expiring(conversationId, messageId, conversationMessageInfo.info.ttl, expiresAt))
        }
    }

    override fun startMessageExpiration(conversationId: ConversationId, messageId: String): Promise<Unit, Exception> {
        return messagePersistenceManager.get(conversationId, messageId) bind {
            startMessageExpiration(conversationId, messageId, it)
        } fail {
            log.error("Unable to start message expiration: {}", it.message, it)
        }
    }

    override fun expireMessages(messages: Map<ConversationId, Collection<String>>): Promise<Unit, Exception> {
        return messagePersistenceManager.expireMessages(messages) successUi  {
            for ((conversationId, messageIds) in messages) {
                messageIds.forEach {
                    messageUpdatesSubject.onNext(MessageUpdateEvent.Expired(conversationId, it))
                }
            }
        }
    }

    override fun getMessagesAwaitingExpiration(): Promise<Map<ConversationId, Collection<MessageInfo>>, Exception> {
        return messagePersistenceManager.getMessagesAwaitingExpiration()
    }
}