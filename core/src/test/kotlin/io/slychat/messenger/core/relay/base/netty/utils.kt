package io.slychat.messenger.core.relay.base.netty

import io.netty.buffer.ByteBuf
import io.slychat.messenger.core.relay.base.HEADER_SIZE
import io.slychat.messenger.core.relay.base.RelayMessage
import io.slychat.messenger.core.relay.base.headerFromBytes

internal fun byteBufToRelayMessage(byteBuf: ByteBuf): RelayMessage {
    val bytes = ByteArray(byteBuf.readableBytes())
    byteBuf.readBytes(bytes)

    val header = headerFromBytes(bytes)
    val contentLength = header.contentLength
    val content = if (contentLength > 0)
        bytes.copyOfRange(HEADER_SIZE, HEADER_SIZE + contentLength)
    else
        ByteArray(0)

    return RelayMessage(header, content)
}
