package io.slychat.messenger.core.relay.base.java

import io.slychat.messenger.core.relay.base.RelayMessage

class RelayClientMessageHandler : ClientMessageHandler {
    override fun write(message: RelayMessage): ByteArray {
        return message.toByteArray()
    }
}