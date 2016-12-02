package io.slychat.messenger.core.relay.base.java

import io.slychat.messenger.core.emptyByteArray
import io.slychat.messenger.core.relay.base.HEADER_SIZE
import io.slychat.messenger.core.relay.base.Header
import io.slychat.messenger.core.relay.base.RelayMessage
import io.slychat.messenger.core.relay.base.headerFromBytes
import java.io.ByteArrayOutputStream
import java.util.*

class RelayServerMessageHandler : ServerMessageHandler {
    private var lastHeader: Header? = null
    private var buffer = ByteArrayOutputStream(HEADER_SIZE)
    private var wantedBufferSize = HEADER_SIZE

    private fun newBuffer(wantedSize: Int) {
        buffer = ByteArrayOutputStream(wantedSize)
        wantedBufferSize = wantedSize
    }

    override fun decode(bytes: ByteArray): List<RelayMessage> {
        var remainingBytes = bytes.size

        val messages = ArrayList<RelayMessage>()

        while (remainingBytes > 0) {
            val header = lastHeader

            val bytesPosition = bytes.size - remainingBytes

            val toRead = Math.min(wantedBufferSize - buffer.size(), remainingBytes)

            buffer.write(bytes, bytesPosition, toRead)

            remainingBytes -= toRead

            if (header == null) {
                if (buffer.size() == HEADER_SIZE) {
                    val header = headerFromBytes(buffer.toByteArray())

                    if (header.contentLength == 0) {
                        messages.add(RelayMessage(header, emptyByteArray()))
                        buffer.reset()
                        continue
                    }

                    newBuffer(header.contentLength)

                    this.lastHeader = header
                }
            }
            else {
                if (buffer.size() == header.contentLength) {
                    messages.add(RelayMessage(header, buffer.toByteArray()))

                    newBuffer(HEADER_SIZE)
                    lastHeader = null
                }
            }
        }

        return messages
    }
}