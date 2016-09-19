package io.slychat.messenger.core.persistence

import nl.komponents.kovenant.Promise

interface MessagePersistenceManager {
    /**
     * Updates the conversation info for the given UserId.
     */
    fun addMessage(conversationId: ConversationId, conversationMessageInfo: ConversationMessageInfo): Promise<Unit, Exception>

    fun addMessages(conversationId: ConversationId, messages: Collection<ConversationMessageInfo>): Promise<Unit, Exception>

    /** Retrieve the last n messages for the given contact starting backwards at the given index. */
    fun getLastMessages(conversationId: ConversationId, startingAt: Int, count: Int): Promise<List<ConversationMessageInfo>, Exception>

    /** Return conversation info for the specified group. */
    fun getConversationInfo(conversationId: ConversationId): Promise<ConversationInfo?, Exception>

    /** Returns info for all available conversations. */
    fun getAllUserConversations(): Promise<List<UserConversation>, Exception>

    /** Returns group data + group conversation info. */
    fun getAllGroupConversations(): Promise<List<GroupConversation>, Exception>

    /** Removes a set of messages from the group log. This also removes an expiring messages, as well as any queued send entries for this message. */
    fun deleteMessages(conversationId: ConversationId, messageIds: Collection<String>): Promise<Unit, Exception>

    /** Clears a group log. */
    fun deleteAllMessages(conversationId: ConversationId): Promise<Unit, Exception>

    /** Marks the given group message as delivered, and updates its sent timestamp. If the message has already been marked as delievered, returns null. */
    fun markMessageAsDelivered(conversationId: ConversationId, messageId: String, timestamp: Long): Promise<ConversationMessageInfo?, Exception>

    /** Resets unread message count for the given contact's conversation. */
    fun markConversationAsRead(conversationId: ConversationId): Promise<List<String>, Exception>

    //XXX I feel like returning stuff here is probably pointless... we only use this from sync messages anyways
    fun markConversationMessagesAsRead(conversationId: ConversationId, messageIds: Collection<String>): Promise<List<String>, Exception>

    //FIXME
    /** Returns all undelivered messages for a given group. */
    fun getUndeliveredMessages(): Promise<Map<ConversationId, List<ConversationMessageInfo>>, Exception>

    fun get(conversationId: ConversationId, messageId: String): Promise<ConversationMessageInfo?, Exception>

    fun expireMessages(messages: Map<ConversationId, Collection<String>>): Promise<Unit, Exception>

    /** Returns true if message was updated, false if message was already expiring. */
    fun setExpiration(conversationId: ConversationId, messageId: String, expiresAt: Long): Promise<Boolean, Exception>

    fun getMessagesAwaitingExpiration(): Promise<List<ExpiringMessage>, Exception>

    fun getConversationDisplayInfo(conversationId: ConversationId): Promise<ConversationDisplayInfo, Exception>
}
