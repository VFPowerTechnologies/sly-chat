package io.slychat.messenger.services.messaging

import io.slychat.messenger.core.UserId
import io.slychat.messenger.core.persistence.*
import io.slychat.messenger.services.MessageUpdateEvent
import nl.komponents.kovenant.Promise
import rx.Observable

interface MessageService {
    val newMessages: Observable<ConversationMessage>
    val messageUpdates: Observable<MessageUpdateEvent>
    val conversationInfoUpdates: Observable<ConversationDisplayInfo>

    //generates Expiring events
    fun startMessageExpiration(conversationId: ConversationId, messageId: String): Promise<Unit, Exception>

    //generates Expired events
    fun expireMessages(messages: Map<ConversationId, Collection<String>>, fromSync: Boolean): Promise<Unit, Exception>

    fun getMessagesAwaitingExpiration(): Promise<List<ExpiringMessage>, Exception>

    //we still need the return value here to broadcast sent messages, so keep it
    fun markMessageAsDelivered(conversationId: ConversationId, messageId: String, timestamp: Long): Promise<ConversationMessageInfo?, Exception>

    fun markConversationMessagesAsRead(conversationId: ConversationId, messageIds: Collection<String>): Promise<Unit, Exception>

    fun markConversationAsRead(conversationId: ConversationId): Promise<Unit, Exception>

    fun addMessage(conversationId: ConversationId, conversationMessageInfo: ConversationMessageInfo): Promise<Unit, Exception>

    fun addFailures(conversationId: ConversationId, messageId: String, failures: Map<UserId, MessageSendFailure>): Promise<Unit, Exception>

    fun deleteMessages(conversationId: ConversationId, messageIds: Collection<String>, fromSync: Boolean): Promise<Unit, Exception>

    fun deleteAllMessages(conversationId: ConversationId): Promise<Unit, Exception>

    fun deleteAllMessagesUntil(conversationId: ConversationId, timestamp: Long): Promise<Unit, Exception>

    fun getAllUserConversations(): Promise<List<UserConversation>, Exception>

    fun getAllGroupConversations(): Promise<List<GroupConversation>, Exception>

    fun getLastMessages(conversationId: ConversationId, startingAt: Int, count: Int): Promise<List<ConversationMessageInfo>, Exception>

    fun getGroupConversation(groupId: GroupId): Promise<GroupConversation?, Exception>

    fun getUserConversation(userId: UserId): Promise<UserConversation?, Exception>
}