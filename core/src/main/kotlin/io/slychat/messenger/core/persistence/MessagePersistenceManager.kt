package io.slychat.messenger.core.persistence

import io.slychat.messenger.core.UserId
import nl.komponents.kovenant.Promise

interface MessagePersistenceManager {
    /**
     * Appends a new message for the given user. Auto-generates a unique message id, along with a timestamp.
     *
     * @param userId
     * @param message The message content itself.
     * @param ttl Unix time in seconds until when the message should be kept. If 0, is kept indefinitely, if < 0 is to be purged on startup.
     */
    fun addMessage(userId: UserId, isSent: Boolean, message: String, ttl: Long): Promise<MessageInfo, Exception>

    fun addSelfMessage(userId: UserId, message: String): Promise<MessageInfo, Exception>

    /** Stores the given list of received messages in the given order. There must not be any empty message lists. */
    fun addReceivedMessages(messages: Map<UserId, List<String>>): Promise<Map<UserId, List<MessageInfo>>, Exception>

    /** Marks a sent message as being received and updates its timestamp to the current time. */
    fun markMessageAsDelivered(userId: UserId, messageId: String): Promise<MessageInfo, Exception>

    /** Retrieve the last n messages for the given contact starting backwards at the given index. */
    fun getLastMessages(userId: UserId, startingAt: Int, count: Int): Promise<List<MessageInfo>, Exception>

    /** Returns all unsent messages. If a contact has no undelievered messages, it won't be included in the result. */
    fun getUndeliveredMessages(): Promise<Map<UserId, List<MessageInfo>>, Exception>

    fun deleteMessages(userId: UserId, messageIds: List<String>): Promise<Unit, Exception>

    fun deleteAllMessages(userId: UserId): Promise<Unit, Exception>
}