package com.vfpowertech.keytap.core.relay.netty

import com.vfpowertech.keytap.core.relay.HEADER_SIZE
import com.vfpowertech.keytap.core.relay.RelayMessage
import com.vfpowertech.keytap.core.relay.headerToByteArray
import io.netty.channel.ChannelHandlerAdapter
import io.netty.channel.ChannelHandlerContext
import io.netty.channel.ChannelPromise

/** Handles converting client->server messages. */
class ClientMessageHandler : ChannelHandlerAdapter() {
    override fun write(ctx: ChannelHandlerContext, msg: Any, promise: ChannelPromise) {
        msg as RelayMessage
        val buf = ctx.alloc().buffer(HEADER_SIZE+msg.header.contentLength)

        val headerData = headerToByteArray(msg.header)
        buf.writeBytes(headerData)
        if (msg.content.size > 0)
            buf.writeBytes(msg.content)

        ctx.writeAndFlush(buf)
    }
}