package io.slychat.messenger.core.relay.base.netty

import io.netty.channel.ChannelHandlerAdapter
import io.netty.channel.ChannelHandlerContext
import io.netty.channel.ChannelPromise
import io.slychat.messenger.core.relay.base.RelayMessage

/** Handles converting client->server messages. */
class ClientMessageHandler : ChannelHandlerAdapter() {
    override fun write(ctx: ChannelHandlerContext, msg: Any, promise: ChannelPromise) {
        msg as RelayMessage

        val buf = msg.toByteBuf(ctx.alloc())

        ctx.writeAndFlush(buf)
    }
}