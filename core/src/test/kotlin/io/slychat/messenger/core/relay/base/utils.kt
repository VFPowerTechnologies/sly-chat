package io.slychat.messenger.core.relay.base

import io.slychat.messenger.core.crypto.randomMessageId
import io.slychat.messenger.core.currentTimestamp
import io.slychat.messenger.core.emptyByteArray
import io.slychat.messenger.core.randomAuthToken
import io.slychat.messenger.core.randomSlyAddress
import org.assertj.core.api.Assertions.assertThat

private fun randomInboundRelayHeader(contentLength: Int): Header {
    return Header(
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
    )
}

internal fun randomInboundRelayMessage(): RelayMessage {
    val content = "testing".toByteArray()
    val contentLength = content.size

    return RelayMessage(randomInboundRelayHeader(contentLength), content)
}

internal fun randomInboundRelayMessageNoContent(): RelayMessage {
    return RelayMessage(randomInboundRelayHeader(0), emptyByteArray())
}

private fun randomRelayOutboundHeader(contentLength: Int): Header {
    return Header(
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
    )
}

internal fun randomOutboundRelayMessageNoContent(): RelayMessage {
    return RelayMessage(randomRelayOutboundHeader(0), emptyByteArray())
}

internal fun randomOutboundRelayMessage(): RelayMessage {
    val content = "testing".toByteArray()
    val contentLength = content.size

    return RelayMessage(randomRelayOutboundHeader(contentLength), content)
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
