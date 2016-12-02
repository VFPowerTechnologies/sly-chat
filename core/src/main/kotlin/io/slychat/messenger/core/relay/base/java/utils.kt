@file:JvmName("JavaRelayUtils")
package io.slychat.messenger.core.relay.base.java

import io.slychat.messenger.core.relay.base.HEADER_SIZE
import io.slychat.messenger.core.relay.base.RelayMessage
import io.slychat.messenger.core.relay.base.headerToByteArray

internal fun RelayMessage.toByteArray(): ByteArray {
    val r = ByteArray(HEADER_SIZE + content.size)

    val headerBytes = headerToByteArray(header)
    System.arraycopy(
        headerBytes,
        0,
        r,
        0,
        headerBytes.size
    )

    if (content.isNotEmpty()) {
        System.arraycopy(
            content,
            0,
            r,
            HEADER_SIZE,
            content.size
        )
    }

    return r
}

