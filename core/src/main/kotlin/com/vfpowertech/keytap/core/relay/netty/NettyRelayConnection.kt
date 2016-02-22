package com.vfpowertech.keytap.core.relay.netty

import com.vfpowertech.keytap.core.relay.RelayMessage
import com.vfpowertech.keytap.core.relay.RelayConnection
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