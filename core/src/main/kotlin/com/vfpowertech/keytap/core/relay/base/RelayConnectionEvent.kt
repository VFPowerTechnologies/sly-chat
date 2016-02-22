package com.vfpowertech.keytap.core.relay.base

/** Low-level RelayConnection event. */
interface RelayConnectionEvent

/** Sent when a connection to the relay is established. */
data class RelayConnectionEstablished(val connection: RelayConnection) : RelayConnectionEvent

class RelayConnectionLost() : RelayConnectionEvent

/** Represents an incoming or outgoing message to the relay server. */
data class RelayMessage(
    val header: Header,
    val content: ByteArray
) : RelayConnectionEvent

