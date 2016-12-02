package io.slychat.messenger.core.relay.base.java

import io.slychat.messenger.core.relay.base.RelayMessage
import io.slychat.messenger.core.relay.base.assertThatMessagesEqual
import io.slychat.messenger.core.relay.base.randomInboundRelayMessage
import io.slychat.messenger.core.relay.base.randomInboundRelayMessageNoContent
import org.junit.Test
import java.io.ByteArrayOutputStream
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class RelayServerMessageHandlerTest {
    private val handler = RelayServerMessageHandler()

    private fun testFullMessage(inboundMessage: RelayMessage) {
        val messages = handler.decode(inboundMessage.toByteArray())

        assertEquals(1, messages.size, "Only expected a single message")

        assertThatMessagesEqual(inboundMessage, messages.first())
    }

    @Test
    fun `it should decode messages with no content`() {
        testFullMessage(randomInboundRelayMessageNoContent())
    }

    @Test
    fun `it should decode messages with content`() {
        testFullMessage(randomInboundRelayMessage())
    }

    @Test
    fun `it should piece together fragmented input`() {
        val inboundMessage = randomInboundRelayMessage()

        val bytes = inboundMessage.toByteArray()

        (0..bytes.size-2).forEach {
            val slice = bytes.sliceArray(it..it)
            assertTrue(handler.decode(slice).isEmpty(), "Got a message back")
        }

        val messages = handler.decode(bytes.sliceArray(bytes.size-1..bytes.size-1))

        assertEquals(1, messages.size, "Only expected a single message")

        assertThatMessagesEqual(inboundMessage, messages.first())
    }

    @Test
    fun `it should decode multiple messages at once`() {
        val inboundMessages = listOf(
            randomInboundRelayMessage(),
            randomInboundRelayMessageNoContent(),
            randomInboundRelayMessage()
        )

        val os = ByteArrayOutputStream()
        inboundMessages.forEach { os.write(it.toByteArray()) }
        val bytes = os.toByteArray()

        val messages = handler.decode(bytes)

        assertEquals(inboundMessages.size, messages.size, "Invalid number of decoded messages")

        messages.forEachIndexed { i, relayMessage ->
            assertThatMessagesEqual(inboundMessages[i], relayMessage)
        }
    }

    @Test
    fun `it should decode multiple messages in a row`() {
        val inboundMessages = listOf(
            randomInboundRelayMessage(),
            randomInboundRelayMessageNoContent(),
            randomInboundRelayMessage()
        )

        inboundMessages.forEach {
            testFullMessage(it)
        }
    }
}