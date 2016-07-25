package io.slychat.messenger.core.persistence

import java.util.*

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