package com.vfpowertech.keytap.core.relay

/** Low-level relay message. */
interface ServerMessage

/** Sent when a connection to the relay is established. */
data class RelayConnectionEstablished(val connection: RelayConnection) : ServerMessage

class RelayConnectionLost() : ServerMessage

/** Represents an incoming or outgoing message to the relay server. */
data class RelayMessage(
    val header: Header,
    val content: ByteArray
) : ServerMessage

