package io.slychat.messenger.core.relay.base.netty

import io.slychat.messenger.core.relay.base.RelayMessage
import io.slychat.messenger.core.relay.base.RelayConnection
import io.netty.channel.Channel

/** Wrapper around the underlying netty Channel. */
class NettyRelayConnection(
    private val channel: Channel
) : RelayConnection {
    override fun sendMessage(message: RelayMessage) {
        channel.write(message)
    }

    override fun disconnect() {
        channel.close()
    }
}