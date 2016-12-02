package io.slychat.messenger.core.relay.base.java

import io.slychat.messenger.core.relay.base.RelayMessage

interface ServerMessageHandler {
    fun decode(bytes: ByteArray): List<RelayMessage>
}