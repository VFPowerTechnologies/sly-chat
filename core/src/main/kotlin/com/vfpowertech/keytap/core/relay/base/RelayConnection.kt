package com.vfpowertech.keytap.core.relay.base

/** Controls a relay server connection. */
interface RelayConnection {
    fun sendMessage(message: RelayMessage)
    fun disconnect()
}