package com.vfpowertech.keytap.core.persistence

import nl.komponents.kovenant.Promise

interface ConversationPersistenceManager {
    /**
     * Creates a new conversation for the given user.
     * Has no effect if the conversation already exists.
     */
    fun createNewConversation(contact: String): Promise<Unit, Exception>

    /** Remove a conversation for the given user. */
    fun deleteConversation(contact: String): Promise<Unit, Exception>

    /**
     * Appends a new message for the given user. Auto-generates a unique message id, along with a timestamp.
     *
     * @param contact If null, is a sent message.
     * @param message The message content itself.
     * @param ttl Unix time in seconds until when the message should be kept. If 0, is kept indefinitely, if < 0 is to be purged on startup.
     */
    fun addMessage(contact: String, isSent: Boolean, message: String, ttl: Long): Promise<MessageInfo, Exception>

    /** Marks a sent message as being received and updates its timestamp to the current time. */
    fun markMessageAsDelivered(contact: String, messageId: String): Promise<MessageInfo, Exception>

    /** Returns a ConversationInfo for the given user. */
    fun getConversationInfo(contact: String): Promise<ConversationInfo, Exception>

    /** Returns info for all available conversations. */
    fun getAllConversations(): Promise<List<ConversationInfo>, Exception>

    /** Resets unread message count for the given contact's conversation. */
    fun markConversationAsRead(contact: String): Promise<Unit, Exception>

    /** Retrieve the last n messages for the given contact starting backwards at the given index. */
    fun getLastMessages(contact: String, startingAt: Int, count: Int): Promise<List<MessageInfo>, Exception>

    /** Returns all unsent messages. */
    fun getUndeliveredMessages(contact: String): Promise<List<MessageInfo>, Exception>
}