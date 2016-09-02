package io.slychat.messenger.core.persistence

import java.util.*

class SenderMessageEntry(val metadata: MessageMetadata, val message: ByteArray) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other?.javaClass != javaClass) return false

        other as SenderMessageEntry

        if (metadata != other.metadata) return false
        if (!Arrays.equals(message, other.message)) return false

        return true
    }

    override fun hashCode(): Int {
        var result = metadata.hashCode()
        result = 31 * result + Arrays.hashCode(message)
        return result
    }

    override fun toString(): String {
        return "SenderMessageEntry(metadata=$metadata, message=${Arrays.toString(message)})"
    }
}