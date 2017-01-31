package io.slychat.messenger.core.relay.base.java

import io.slychat.messenger.testutils.TestException
import org.assertj.core.api.Assertions.assertThat
import org.junit.Rule
import org.junit.Test
import org.junit.rules.Timeout
import java.io.InputStream
import java.util.concurrent.ArrayBlockingQueue
import kotlin.test.assertTrue
import kotlin.test.fail

class ReaderTest {
    private val messageQueue = ArrayBlockingQueue<ConnectionManagerMessage>(10)

    @Rule
    @JvmField
    val timeoutRule = Timeout(500)

    private fun checkForMessage(checker: (ConnectionManagerMessage) -> Boolean) {

        val message = messageQueue.poll() ?: fail("No message received")

        if (!checker(message))
            fail("Invalid message type: $message")
    }

    @Test
    fun `it should write an EOF message when reaching EOF`() {
        val inputStream = object : InputStream() {
            override fun read(): Int {
                return -1
            }
        }

        val reader = Reader(inputStream, messageQueue)

        reader.run()

        checkForMessage { it is ConnectionManagerMessage.EOF }
    }

    @Test
    fun `it should write a ReaderError message when an exception is thrown`() {
        val inputStream = object : InputStream() {
            override fun read(): Int {
                throw TestException()
            }
        }

        val reader = Reader(inputStream, messageQueue)

        reader.run()

        checkForMessage {
            if (it is ConnectionManagerMessage.ReaderError) {
                assertTrue(it.throwable is TestException, "Invalid exception: ${it.throwable}")
                true
            }
            else
                false
        }
    }

    @Test
    fun `it should write a ReaderData message when reading data`() {
        val inputStream = object : InputStream() {
            private var data: Int? = 1

            override fun read(): Int {
                val d = data

                return if (d != null) {
                    data = null
                    return d
                }
                else
                    -1
            }
        }

        val reader = Reader(inputStream, messageQueue)

        reader.run()

        checkForMessage {
            if (it is ConnectionManagerMessage.ReaderData) {
                assertThat(it.bytes).apply {
                    describedAs("Invalid data")
                    isEqualTo(byteArrayOf(1))
                }

                true
            }

            else
                false
        }
    }
}