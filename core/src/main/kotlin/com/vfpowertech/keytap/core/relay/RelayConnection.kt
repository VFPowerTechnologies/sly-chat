package com.vfpowertech.keytap.core.relay

/** Controls a relay server connection. */
interface RelayConnection {
    fun sendMessage(message: RelayMessage)
    fun disconnect()
}