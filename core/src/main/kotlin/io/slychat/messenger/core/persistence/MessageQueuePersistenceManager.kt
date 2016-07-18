package io.slychat.messenger.core.persistence

import io.slychat.messenger.core.UserId
import nl.komponents.kovenant.Promise
import java.util.*

/**
 * @property timestamp Used for ordering the message in the queue.
 */
class QueuedMessage(
    val userId: UserId,
    val messageId: String,
    val timestamp: Long,
    val serialized: ByteArray
) {

    override fun toString(): String {
        return "QueuedMessage(userId=$userId, messageId='$messageId', timestamp=$timestamp)"
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other?.javaClass != javaClass) return false

        other as QueuedMessage

        if (userId != other.userId) return false
        if (messageId != other.messageId) return false
        if (timestamp != other.timestamp) return false
        if (!Arrays.equals(serialized, other.serialized)) return false

        return true
    }

    override fun hashCode(): Int {
        var result = userId.hashCode()
        result = 31 * result + messageId.hashCode()
        result = 31 * result + timestamp.hashCode()
        result = 31 * result + Arrays.hashCode(serialized)
        return result
    }
}

interface MessageQueuePersistenceManager {
    fun add(queuedMessage: QueuedMessage): Promise<Unit, Exception>

    fun remove(userId: UserId, messageId: String): Promise<Unit, Exception>

    /** Returned list is ordered by timestamp. */
    fun getUndelivered(): Promise<List<QueuedMessage>, Exception>
}