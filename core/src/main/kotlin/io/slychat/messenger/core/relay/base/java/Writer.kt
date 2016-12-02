package io.slychat.messenger.core.relay.base.java

import io.slychat.messenger.core.enforceExhaustive
import java.io.OutputStream
import java.util.*
import java.util.concurrent.BlockingQueue

internal class Writer(
    private val outputStream: OutputStream,
    private val messageQueue: BlockingQueue<ConnectionManagerMessage>,
    private val writeQueue: BlockingQueue<Work>
) : Runnable {
    sealed class Work {
        class Write(val data: ByteArray) : Work() {
            override fun equals(other: Any?): Boolean {
                if (this === other) return true
                if (other?.javaClass != javaClass) return false

                other as Write

                if (!Arrays.equals(data, other.data)) return false

                return true
            }

            override fun hashCode(): Int {
                return Arrays.hashCode(data)
            }
        }

        class Disconnect() : Work() {
            override fun equals(other: Any?): Boolean {
                return other is Disconnect
            }

            override fun hashCode(): Int {
                return 0
            }
        }
    }

    override fun run() {
        try {
            main()
        }
        catch (t: Throwable) {
            messageQueue.put(ConnectionManagerMessage.WriterError(t))
        }
    }

    private fun main() {
        var keepRunning = true

        while (keepRunning) {
            val v = writeQueue.take() ?: continue

            when (v) {
                is Writer.Work.Write -> {
                    outputStream.write(v.data)
                }

                is Writer.Work.Disconnect -> {
                    keepRunning = false
                }
            }.enforceExhaustive()
        }
    }
}