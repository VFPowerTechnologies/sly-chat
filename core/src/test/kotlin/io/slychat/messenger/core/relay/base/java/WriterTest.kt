package io.slychat.messenger.core.relay.base.java

import io.slychat.messenger.testutils.TestException
import org.assertj.core.api.Assertions.assertThat
import org.junit.Rule
import org.junit.Test
import org.junit.rules.Timeout
import java.io.ByteArrayOutputStream
import java.io.OutputStream
import java.util.concurrent.ArrayBlockingQueue
import kotlin.test.assertEquals
import kotlin.test.fail

class WriterTest {
    private val messageQueue = ArrayBlockingQueue<ConnectionManagerMessage>(10)
    private val writeQueue = ArrayBlockingQueue<Writer.Work>(10)
    private val outputStream = ByteArrayOutputStream()
    private val writer = Writer(outputStream, messageQueue, writeQueue)

    @Rule
    @JvmField
    val timeoutRule = Timeout(500)

    @Test
    fun `it should stop reading when receiving Disconnect messages`() {
        writeQueue.put(Writer.Work.Disconnect())

        writer.run()
    }

    @Test
    fun `it should write WriterError if an exception is thrown during writing`() {
        val errorStream = object : OutputStream() {
            override fun write(b: Int) {
                throw TestException()
            }
        }

        val writer = Writer(errorStream, messageQueue, writeQueue)
        writeQueue.put(Writer.Work.Write(byteArrayOf(10)))

        writer.run()

        val got = messageQueue.poll()

        when (got) {
            null ->
                fail("No message written")

            is ConnectionManagerMessage.WriterError ->
                assertEquals<Class<out Throwable>>(TestException::class.java, got.throwable.javaClass, "Invalid exception type")

            else ->
                fail("Received invalid message: $got")
        }
    }

    @Test
    fun `it should write data to the output stream when receiving Write messages`() {
        val data = ByteArray(10)

        writeQueue.put(Writer.Work.Write(data))
        writeQueue.put(Writer.Work.Disconnect())

        writer.run()

        assertThat(outputStream.toByteArray()).apply {
            describedAs("Wrote invalid data")
            isEqualTo(data)
        }
    }
}