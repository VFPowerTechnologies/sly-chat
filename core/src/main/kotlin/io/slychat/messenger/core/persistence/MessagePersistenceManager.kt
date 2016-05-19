package io.slychat.messenger.core.persistence

import io.slychat.messenger.core.SlyAddress
import io.slychat.messenger.core.UserId
import nl.komponents.kovenant.Promise

data class PackageId(
    val address: SlyAddress,
    val messageId: String
)

data class Package(
    val id: PackageId,
    val timestamp: Long,
    val payload: String
)

interface MessagePersistenceManager {
    /**
     * Updates the conversation info for the given UserId.
     */
    fun addMessage(userId: UserId, messageInfo: MessageInfo): Promise<MessageInfo, Exception>

    fun addMessages(userId: UserId, messages: List<MessageInfo>): Promise<List<MessageInfo>, Exception>

    /** Marks a sent message as being received and updates its timestamp to the current time. */
    fun markMessageAsDelivered(userId: UserId, messageId: String): Promise<MessageInfo, Exception>

    /** Retrieve the last n messages for the given contact starting backwards at the given index. */
    fun getLastMessages(userId: UserId, startingAt: Int, count: Int): Promise<List<MessageInfo>, Exception>

    /** Returns all unsent messages. If a contact has no undelievered messages, it won't be included in the result. */
    fun getUndeliveredMessages(): Promise<Map<UserId, List<MessageInfo>>, Exception>

    fun deleteMessages(userId: UserId, messageIds: List<String>): Promise<Unit, Exception>

    fun deleteAllMessages(userId: UserId): Promise<Unit, Exception>

    /** Stores a received message prior to decryption. */
    fun addToQueue(message: Package): Promise<Unit, Exception>

    fun addToQueue(messages: List<Package>): Promise<Unit, Exception>

    fun removeFromQueue(messageId: PackageId): Promise<Unit, Exception>

    fun removeFromQueue(messageIds: List<PackageId>): Promise<Unit, Exception>

    fun getQueuedMessages(): Promise<List<Package>, Exception>
}