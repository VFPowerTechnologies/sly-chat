package io.slychat.messenger.core.persistence

import io.slychat.messenger.core.UserId
import nl.komponents.kovenant.Promise
import java.util.*

/** General category of messages. */
enum class MessageCategory {
    /** Single recipient text message. */
    TEXT_SINGLE,
    /** Group text message. */
    TEXT_GROUP,
    /** All other messages (group control messages, etc). */
    OTHER
}

/** Various data used to identify the message. */
data class MessageMetadata(
    val userId: UserId,
    val groupId: GroupId?,
    val category: MessageCategory,
    val messageId: String
) {
    init {
        if (category == MessageCategory.TEXT_GROUP && groupId == null)
            throw IllegalArgumentException("groupId must be non-null when category is TEXT_GROUP")

        else if (category == MessageCategory.TEXT_SINGLE && groupId != null)
            throw IllegalArgumentException("groupId must be null when category is TEXT_SINGLE")
    }
}

/**
 * @property timestamp Used for ordering the message in the queue.
 */
class QueuedMessage(
    val metadata: MessageMetadata,
    val timestamp: Long,
    val serialized: ByteArray
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other?.javaClass != javaClass) return false

        other as QueuedMessage

        if (metadata != other.metadata) return false
        if (timestamp != other.timestamp) return false
        if (!Arrays.equals(serialized, other.serialized)) return false

        return true
    }

    override fun hashCode(): Int {
        var result = metadata.hashCode()
        result = 31 * result + timestamp.hashCode()
        result = 31 * result + Arrays.hashCode(serialized)
        return result
    }

    override fun toString(): String {
        return "QueuedMessage(metaData=$metadata, timestamp=$timestamp)"
    }
}

interface MessageQueuePersistenceManager {
    fun add(queuedMessage: QueuedMessage): Promise<Unit, Exception>

    fun remove(userId: UserId, messageId: String): Promise<Unit, Exception>

    /** Returned list is ordered by timestamp. */
    fun getUndelivered(): Promise<List<QueuedMessage>, Exception>
}