package io.slychat.messenger.android.activites.services

import io.slychat.messenger.core.UserId
import io.slychat.messenger.core.persistence.*
import io.slychat.messenger.services.MessageUpdateEvent
import io.slychat.messenger.services.messaging.ConversationMessage
import nl.komponents.kovenant.Promise

interface MessengerService {

    fun fetchAllConversation (): Promise<MutableMap<UserId, UserConversation>, Exception>

    fun getUserConversation(userId: UserId): Promise<UserConversation?, Exception>

    fun getGroupConversation(groupId: GroupId): Promise<GroupConversation?, Exception>

    fun getActualSortedConversation(convo: MutableMap<UserId, UserConversation>): List<UserConversation>

    fun getSortedByNameConversation (convo: MutableMap<UserId, UserConversation>): List<UserConversation>

    fun addNewMessageListener(listener: (ConversationMessage) -> Unit)

    fun addMessageUpdateListener(listener: (MessageUpdateEvent) -> Unit)

    fun clearListeners()

    fun fetchMessageFor(conversationId: ConversationId, from: Int, to: Int): Promise<List<ConversationMessageInfo>, Exception>

    fun sendMessageTo(conversationId: ConversationId, message: String, ttl: Long): Promise<Unit, Exception>

    fun deleteConversation(conversationId: ConversationId): Promise<Unit, Exception>

    fun startMessageExpiration(conversationId: ConversationId, messageId: String): Promise<Unit, Exception>

    fun deleteMessage(conversationId: ConversationId, messageId: String): Promise<Unit, Exception>
}