@file:JvmName("NettyUtils")
package io.slychat.messenger.core.relay.base.netty

import io.netty.buffer.ByteBuf
import io.netty.buffer.ByteBufAllocator
import io.slychat.messenger.core.relay.base.HEADER_SIZE
import io.slychat.messenger.core.relay.base.RelayMessage
import io.slychat.messenger.core.relay.base.headerToByteArray

internal fun RelayMessage.toByteBuf(allocator: ByteBufAllocator): ByteBuf {
    val buf = allocator.buffer(HEADER_SIZE + header.contentLength)

    val headerData = headerToByteArray(header)
    buf.writeBytes(headerData)
    if (content.size > 0)
        buf.writeBytes(content)

    return buf
}