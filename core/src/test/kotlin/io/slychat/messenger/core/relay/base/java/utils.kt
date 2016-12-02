package io.slychat.messenger.core.relay.base.java

import io.slychat.messenger.core.relay.base.HEADER_SIZE
import io.slychat.messenger.core.relay.base.RelayMessage
import io.slychat.messenger.core.relay.base.headerFromBytes

fun byteArrayToRelayMessage(bytes: ByteArray): RelayMessage {
    val header = headerFromBytes(bytes)
    val content = ByteArray(header.contentLength)

    if (header.contentLength > 0)
        System.arraycopy(bytes, HEADER_SIZE, content, 0, header.contentLength)

    return RelayMessage(header, content)
}