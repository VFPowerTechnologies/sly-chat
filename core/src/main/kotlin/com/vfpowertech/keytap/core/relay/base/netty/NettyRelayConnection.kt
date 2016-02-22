package com.vfpowertech.keytap.core.relay.base.netty

import com.vfpowertech.keytap.core.relay.base.RelayMessage
import com.vfpowertech.keytap.core.relay.base.RelayConnection
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