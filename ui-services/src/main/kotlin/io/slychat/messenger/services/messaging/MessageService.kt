package io.slychat.messenger.services.messaging

import io.slychat.messenger.core.persistence.ConversationId
import io.slychat.messenger.core.persistence.ConversationMessageInfo
import io.slychat.messenger.services.MessageUpdateEvent
import nl.komponents.kovenant.Promise
import rx.Observable

interface MessageService {
    val newMessages: Observable<ConversationMessage>
    val messageUpdates: Observable<MessageUpdateEvent>

    //TODO
    //fun destroyMessages(conversationId: ConversationId, messageIds: Collection<String>): Promise<Unit, Exception>
    //we still need the return value here to broadcast sent messages, so keep it
    fun markMessageAsDelivered(conversationId: ConversationId, messageId: String, timestamp: Long): Promise<ConversationMessageInfo?, Exception>
    fun markConversationAsRead(conversationId: ConversationId): Promise<Unit, Exception>
    fun addNewMessage(conversationId: ConversationId, conversationMessageInfo: ConversationMessageInfo): Promise<Unit, Exception>
}