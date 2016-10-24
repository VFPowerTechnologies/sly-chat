package io.slychat.messenger.core.relay.base.netty

import io.netty.buffer.ByteBuf
import io.slychat.messenger.core.crypto.randomMessageId
import io.slychat.messenger.core.currentTimestamp
import io.slychat.messenger.core.randomAuthToken
import io.slychat.messenger.core.randomSlyAddress
import io.slychat.messenger.core.relay.base.*
import org.assertj.core.api.Assertions.assertThat

internal fun randomInboundRelayMessage(): RelayMessage {
    val content = "testing".toByteArray()
    val contentLength = content.size

    return RelayMessage(
        Header(
            PROTOCOL_VERSION_1,
            contentLength,
            randomAuthToken().string,
            randomSlyAddress().asString(),
            "",
            randomMessageId(),
            0,
            1,
            currentTimestamp(),
            CommandCode.CLIENT_SEND_MESSAGE
        ),
        content
    )
}

internal fun randomOutboundRelayMessage(): RelayMessage {
    val content = "testing".toByteArray()
    val contentLength = content.size

    return RelayMessage(
        Header(
            PROTOCOL_VERSION_1,
            contentLength,
            randomAuthToken().string,
            randomSlyAddress().asString(),
            randomSlyAddress().asString(),
            randomMessageId(),
            0,
            1,
            currentTimestamp(),
            CommandCode.CLIENT_SEND_MESSAGE
        ),
        content
    )
}

internal fun assertThatMessagesEqual(expected: RelayMessage, actual: RelayMessage) {
    assertThat(actual.header).apply {
        `as`("Headers must match")
        isEqualToComparingFieldByField(expected.header)
    }

    assertThat(actual.content).apply {
        `as`("Message content must match")
        isEqualTo(expected.content)
    }
}

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
