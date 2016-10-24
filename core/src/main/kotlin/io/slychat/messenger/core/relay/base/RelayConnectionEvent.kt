package io.slychat.messenger.core.relay.base

import java.util.*

/** Low-level RelayConnection event. */
interface RelayConnectionEvent

/** Sent when a connection to the relay is established. */
data class RelayConnectionEstablished(val connection: RelayConnection) : RelayConnectionEvent

class RelayConnectionLost() : RelayConnectionEvent

/** Represents an incoming or outgoing message to the relay server. */
class RelayMessage(
    val header: Header,
    val content: ByteArray
) : RelayConnectionEvent {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other?.javaClass != javaClass) return false

        other as RelayMessage

        if (header != other.header) return false
        if (!Arrays.equals(content, other.content)) return false

        return true
    }

    override fun hashCode(): Int {
        var result = header.hashCode()
        result = 31 * result + Arrays.hashCode(content)
        return result
    }

    override fun toString(): String {
        return "RelayMessage(header=$header, content=[${content.size}b])"
    }
}

