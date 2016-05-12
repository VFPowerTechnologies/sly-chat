package io.slychat.messenger.core.relay.base

/** Controls a relay server connection. */
interface RelayConnection {
    fun sendMessage(message: RelayMessage)
    fun disconnect()
}