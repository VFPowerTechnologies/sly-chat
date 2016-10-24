package io.slychat.messenger.core.relay.base.netty

import io.netty.buffer.UnpooledByteBufAllocator
import io.netty.channel.embedded.EmbeddedChannel
import io.slychat.messenger.core.crypto.randomMessageId
import io.slychat.messenger.core.currentTimestamp
import io.slychat.messenger.core.randomAuthToken
import io.slychat.messenger.core.randomUserId
import io.slychat.messenger.core.relay.base.*
import io.slychat.messenger.testutils.testSubscriber
import nl.komponents.kovenant.deferred
import org.assertj.core.api.Assertions.assertThat
import org.junit.Test
import rx.subjects.PublishSubject

class ServerMessageHandlerTest {
    private val allocator = UnpooledByteBufAllocator.DEFAULT

    private val observer = PublishSubject.create<RelayConnectionEvent>()
    private val testSubscriber = observer.testSubscriber()

    private val sslHandshakeComplete = deferred<Boolean, Exception>()
    private val handler = ServerMessageHandler(observer, sslHandshakeComplete, true)

    private val ec = EmbeddedChannel(handler)

    private fun randomRelayMessage(): RelayMessage {
        val content = "testing".toByteArray()
        val contentLength = content.size

        return RelayMessage(
            Header(
                PROTOCOL_VERSION_1,
                contentLength,
                randomAuthToken().string,
                randomUserId().toString(),
                randomUserId().toString(),
                randomMessageId(),
                0,
                1,
                currentTimestamp(),
                CommandCode.CLIENT_SEND_MESSAGE
            ),
            content
        )
    }

    private fun assertThatMessagesEqual(expected: RelayMessage, actual: RelayMessage) {
        assertThat(actual.header).apply {
            `as`("Headers must match")
            isEqualToComparingFieldByField(expected.header)
        }

        assertThat(actual.content).apply {
            `as`("Message content must match")
            isEqualTo(expected.content)
        }
    }

    @Test
    fun `it should properly piece together unfragmented input`() {
        val inputMessage = randomRelayMessage()
        val buf = inputMessage.toByteBuf(allocator)

        ec.writeInbound(buf)

        val message = testSubscriber.onNextEvents.first() as RelayMessage
        assertThatMessagesEqual(inputMessage, message)
    }

    @Test
    fun `it should properly piece together fragmented input`() {
        val inputMessage = randomRelayMessage()
        val buf = inputMessage.toByteBuf(allocator)

        (0..buf.capacity()-1).forEach {
            val slice = buf.slice(it, 1)
            //decoder will attempt to .release
            slice.retain()
            ec.writeInbound(slice)
        }

        val message = testSubscriber.onNextEvents.first() as RelayMessage
        assertThatMessagesEqual(inputMessage, message)
    }
}