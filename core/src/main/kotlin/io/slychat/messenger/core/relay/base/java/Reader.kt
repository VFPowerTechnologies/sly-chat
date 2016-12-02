package io.slychat.messenger.core.relay.base.java

import java.io.InputStream
import java.util.*
import java.util.concurrent.BlockingQueue

private const val BUFFER_SIZE = 4096

internal class Reader(
    private val inputStream: InputStream,
    private val messageQueue: BlockingQueue<ConnectionManagerMessage>
) : Runnable {
    override fun run() {
        try {
            main()
        }
        catch (t: Throwable) {
            messageQueue.put(ConnectionManagerMessage.ReaderError(t))
        }
    }

    private fun main() {
        val buffer = ByteArray(BUFFER_SIZE)

        while (true) {
            val read = inputStream.read(buffer)
            if (read <= 0)
                break

            val copy = Arrays.copyOfRange(buffer, 0, read)
            messageQueue.put(ConnectionManagerMessage.ReaderData(copy))
        }

        messageQueue.put(ConnectionManagerMessage.EOF())
    }
}