package io.slychat.messenger.core.relay.base.java

import java.io.InputStream
import java.io.OutputStream

/** Manages creating/closing a socket. */
internal interface SocketConnector {
    fun connect(): Pair<InputStream, OutputStream>
    fun disconnect()
}