package io.slychat.messenger.core.relay.base.java

import io.slychat.messenger.core.relay.base.RelayMessage

interface ClientMessageHandler {
    fun write(message: RelayMessage): ByteArray
}