package io.slychat.messenger.services.messaging

import io.slychat.messenger.core.UserId
import io.slychat.messenger.core.currentTimestamp
import io.slychat.messenger.core.persistence.*
import io.slychat.messenger.services.MessageUpdateEvent
import io.slychat.messenger.services.mapUi
import nl.komponents.kovenant.Promise
import nl.komponents.kovenant.functional.bind
import nl.komponents.kovenant.functional.map
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

    private val conversationInfoUpdatesSubject = PublishSubject.create<ConversationDisplayInfo>()
    override val conversationInfoUpdates: Observable<ConversationDisplayInfo> = conversationInfoUpdatesSubject.distinctUntilChanged()

    override fun markMessageAsDelivered(conversationId: ConversationId, messageId: String, timestamp: Long): Promise<ConversationMessageInfo?, Exception> {
        return messagePersistenceManager.markMessageAsDelivered(conversationId, messageId, timestamp) successUi {
            if (it != null) {
                val update = MessageUpdateEvent.Delivered(conversationId, messageId, timestamp)
                messageUpdatesSubject.onNext(update)
            }
        }
    }

    private fun emitCurrentConversationDisplayInfo(conversationId: ConversationId) {
        messagePersistenceManager.getConversationDisplayInfo(conversationId) successUi {
            conversationInfoUpdatesSubject.onNext(it)
        } fail {
            log.error("Unable to fetch conversation display info for {}: {}", conversationId, it.message, it)
        }
    }

    override fun addMessage(conversationId: ConversationId, conversationMessageInfo: ConversationMessageInfo, receivedAttachments: List<ReceivedAttachment>): Promise<Unit, Exception> {
        return messagePersistenceManager.addMessage(conversationId, conversationMessageInfo, receivedAttachments) mapUi {
            val conversationMessage = ConversationMessage(conversationId, conversationMessageInfo)

            newMessagesSubject.onNext(conversationMessage)
        } success {
            emitCurrentConversationDisplayInfo(conversationId)
        }
    }

    override fun addFailures(conversationId: ConversationId, messageId: String, failures: Map<UserId, MessageSendFailure>): Promise<Unit, Exception> {
        return messagePersistenceManager.addFailures(conversationId, messageId, failures) mapUi {
            messageUpdatesSubject.onNext(MessageUpdateEvent.DeliveryFailed(conversationId, messageId, it.failures))
        }
    }

    override fun markConversationMessagesAsRead(conversationId: ConversationId, messageIds: Collection<String>): Promise<Unit, Exception> {
        return messagePersistenceManager.markConversationMessagesAsRead(conversationId, messageIds) success { messageIds ->
            if (messageIds.isNotEmpty()) {
                emitCurrentConversationDisplayInfo(conversationId)
                emitMessagesReadEvent(conversationId, messageIds, true)
            }
        } map { Unit }
    }

    override fun markConversationAsRead(conversationId: ConversationId): Promise<Unit, Exception> {
        return messagePersistenceManager.markConversationAsRead(conversationId) success { messageIds ->
            if (messageIds.isNotEmpty()) {
                emitCurrentConversationDisplayInfo(conversationId)
                emitMessagesReadEvent(conversationId, messageIds, false)
            }
        } map { Unit }
    }

    private fun emitMessagesReadEvent(conversationId: ConversationId, messageIds: List<String>, fromSync: Boolean) {
        if (messageIds.isEmpty())
            return

        val event = MessageUpdateEvent.Read(conversationId, messageIds, fromSync)
        messageUpdatesSubject.onNext(event)
    }

    override fun deleteMessages(conversationId: ConversationId, messageIds: Collection<String>, fromSync: Boolean): Promise<Unit, Exception> {
        return messagePersistenceManager.deleteMessages(conversationId, messageIds) successUi {
            messageUpdatesSubject.onNext(MessageUpdateEvent.Deleted(conversationId, messageIds.toList(), fromSync))
            emitCurrentConversationDisplayInfo(conversationId)
        }
    }

    //this can be called without opening the conversation, so we might have unread messages
    override fun deleteAllMessages(conversationId: ConversationId): Promise<Unit, Exception> {
        val p = messagePersistenceManager.deleteAllMessages(conversationId)

        p successUi { lastMessageTimestamp ->
            if (lastMessageTimestamp != null) {
                messageUpdatesSubject.onNext(MessageUpdateEvent.DeletedAll(conversationId, lastMessageTimestamp, false))
                emitCurrentConversationDisplayInfo(conversationId)
            }
        }

        return p map { Unit }
    }

    override fun deleteAllMessagesUntil(conversationId: ConversationId, timestamp: Long): Promise<Unit, Exception> {
        return messagePersistenceManager.deleteAllMessagesUntil(conversationId, timestamp) successUi {
            messageUpdatesSubject.onNext(MessageUpdateEvent.DeletedAll(conversationId, timestamp, true))
            emitCurrentConversationDisplayInfo(conversationId)
        }
    }

    override fun getAllUserConversations(): Promise<List<UserConversation>, Exception> {
        return messagePersistenceManager.getAllUserConversations()
    }

    override fun getAllGroupConversations(): Promise<List<GroupConversation>, Exception> {
        return messagePersistenceManager.getAllGroupConversations()
    }

    override fun getGroupConversation(groupId: GroupId): Promise<GroupConversation?, Exception> {
        return messagePersistenceManager.getGroupConversation(groupId)
    }

    override fun getUserConversation(userId: UserId): Promise<UserConversation?, Exception> {
        return messagePersistenceManager.getUserConversation(userId)
    }

    override fun getLastMessages(conversationId: ConversationId, startingAt: Int, count: Int): Promise<List<ConversationMessageInfo>, Exception> {
        return messagePersistenceManager.getLastMessages(conversationId, startingAt, count)
    }

    override fun getAllReceivedAttachments(): Promise<List<ReceivedAttachment>, Exception> {
        return messagePersistenceManager.getAllReceivedAttachments()
    }

    override fun deleteReceivedAttachments(completed: List<AttachmentId>, markInline: List<AttachmentId>): Promise<Unit, Exception> {
        return messagePersistenceManager.deleteReceivedAttachments(completed, markInline)
    }

    private fun startMessageExpiration(conversationId: ConversationId, messageId: String, conversationMessageInfo: ConversationMessageInfo?): Promise<Unit, Exception> {
        if (conversationMessageInfo == null) {
            log.warn("Invalid message id {} for conversation {}", messageId, conversationId)
            return Promise.ofSuccess(Unit)
        }

        val expiresAt = currentTimestamp() + conversationMessageInfo.info.ttlMs

        return messagePersistenceManager.setExpiration(conversationId, messageId, expiresAt) mapUi { wasUpdated ->
            if (wasUpdated)
                messageUpdatesSubject.onNext(MessageUpdateEvent.Expiring(conversationId, messageId, conversationMessageInfo.info.ttlMs, expiresAt))
        }
    }

    override fun startMessageExpiration(conversationId: ConversationId, messageId: String): Promise<Unit, Exception> {
        return messagePersistenceManager.get(conversationId, messageId) bind {
            startMessageExpiration(conversationId, messageId, it)
        } fail {
            log.error("Unable to start message expiration: {}", it.message, it)
        }
    }

    override fun expireMessages(messages: Map<ConversationId, Collection<String>>, fromSync: Boolean): Promise<Unit, Exception> {
        return messagePersistenceManager.expireMessages(messages) successUi  {
            for ((conversationId, messageIds) in messages) {
                messageIds.forEach {
                    messageUpdatesSubject.onNext(MessageUpdateEvent.Expired(conversationId, it, fromSync))
                }

                emitCurrentConversationDisplayInfo(conversationId)
            }
        }
    }

    override fun getMessagesAwaitingExpiration(): Promise<List<ExpiringMessage>, Exception> {
        return messagePersistenceManager.getMessagesAwaitingExpiration()
    }
}