package io.slychat.messenger.core.relay.base.java

import io.slychat.messenger.core.relay.base.assertThatMessagesEqual
import io.slychat.messenger.core.relay.base.randomOutboundRelayMessage
import io.slychat.messenger.core.relay.base.randomOutboundRelayMessageNoContent
import org.junit.Test

class RelayClientMessageHandlerTest {
    private val handler = RelayClientMessageHandler()

    @Test
    fun `it should properly serialize a message with no content`() {
        val inputMessage = randomOutboundRelayMessageNoContent()

        val bytes = handler.write(inputMessage)

        val outboundMessage = byteArrayToRelayMessage(bytes)

        assertThatMessagesEqual(inputMessage, outboundMessage)
    }

    @Test
    fun `it should properly serialize a message with content`() {
        val inputMessage = randomOutboundRelayMessage()

        val bytes = handler.write(inputMessage)

        val outboundMessage = byteArrayToRelayMessage(bytes)

        assertThatMessagesEqual(inputMessage, outboundMessage)
    }
}