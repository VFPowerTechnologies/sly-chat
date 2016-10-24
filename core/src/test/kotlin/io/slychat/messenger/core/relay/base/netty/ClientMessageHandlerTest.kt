package io.slychat.messenger.core.relay.base.netty

import io.netty.buffer.ByteBuf
import io.netty.channel.embedded.EmbeddedChannel
import org.junit.Test

class ClientMessageHandlerTest {
    private val handler = ClientMessageHandler()

    private val ec = EmbeddedChannel(handler)

    @Test
    fun `it should properly serialize a relay message`() {
        val inputMessage = randomRelayMessage()

        ec.writeOutbound(inputMessage)

        val outbound = ec.outboundMessages().first()
        outbound as ByteBuf

        val outboundMessage = byteBufToRelayMessage(outbound)

        assertThatMessagesEqual(inputMessage, outboundMessage)
    }
}