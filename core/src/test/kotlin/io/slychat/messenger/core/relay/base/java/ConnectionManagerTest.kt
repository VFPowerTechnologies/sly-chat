package io.slychat.messenger.core.relay.base.java

import com.nhaarman.mockito_kotlin.mock
import com.nhaarman.mockito_kotlin.whenever
import io.slychat.messenger.core.relay.base.*
import io.slychat.messenger.testutils.TestException
import io.slychat.messenger.testutils.cond
import org.assertj.core.api.Assertions
import org.assertj.core.api.Assertions.assertThat
import org.junit.Rule
import org.junit.Test
import org.junit.rules.Timeout
import rx.observers.TestSubscriber
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.util.*
import java.util.concurrent.BlockingQueue
import kotlin.test.assertEquals
import kotlin.test.fail

class ConnectionManagerTest {
    private class DummyReaderWriterFactory : ReaderWriterFactory {
        var readerStream: InputStream? = null
            private set

        var writerStream: OutputStream? = null
            private set

        lateinit var writerQueue: BlockingQueue<Writer.Work>
        lateinit var messagesQueue: BlockingQueue<ConnectionManagerMessage>

        override fun createReader(inputStream: InputStream, messages: BlockingQueue<ConnectionManagerMessage>) {
            readerStream = inputStream
            messagesQueue = messages
        }

        override fun createWriter(outputStream: OutputStream, messages: BlockingQueue<ConnectionManagerMessage>, writerQueue: BlockingQueue<Writer.Work>) {
            writerStream = outputStream
            messagesQueue = messages
            this.writerQueue = writerQueue
        }
    }

    private class DummySocketConnector : SocketConnector {
        private var disconnectWasCalled = false

        private var connectWasCalled = false

        val outputStream = ByteArrayOutputStream()
        val inputStream = ByteArrayInputStream(byteArrayOf(1))

        override fun connect(): Pair<InputStream, OutputStream> {
            connectWasCalled = true

            return inputStream to outputStream
        }

        override fun disconnect() {
            disconnectWasCalled = true
        }

        fun assertDisconnectWasCalled() {
            if (!disconnectWasCalled)
                fail("disconnect() not called")
        }

        fun assertConnectWasCalled() {
            if (!connectWasCalled)
                fail("connect() not called")
        }
    }

    @Rule
    @JvmField
    val timeoutRule = Timeout(500)

    private inline fun <reified T : RelayConnectionEvent> TestSubscriber<RelayConnectionEvent>.filterEvents(): List<T> {
        return this.onNextEvents.filter { it is T }.map { it as T }
    }

    private fun testDisconnectFromMessage(message: ConnectionManagerMessage) {
        val testSubscriber = TestSubscriber<RelayConnectionEvent>()

        val socketConnector = DummySocketConnector()

        val readerWriterFactory = DummyReaderWriterFactory()

        val connectionManager = ConnectionManager(
            socketConnector,
            testSubscriber,
            mock(),
            mock(),
            readerWriterFactory
        )

        connectionManager.connect()

        readerWriterFactory.messagesQueue.put(message)

        connectionManager.processMessages(false)

        assertDisconnected(testSubscriber, socketConnector, readerWriterFactory)
    }

    private fun assertDisconnected(testSubscriber: TestSubscriber<RelayConnectionEvent>, socketConnector: DummySocketConnector, readerWriterFactory: DummyReaderWriterFactory) {
        socketConnector.assertDisconnectWasCalled()
        Assertions.assertThat(testSubscriber.onNextEvents).apply {
            describedAs("Should emit a RelayConnectionLost event")
            contains(RelayConnectionLost())
        }

        testSubscriber.assertCompleted()

        val writerMessages = ArrayList<Writer.Work>()
        readerWriterFactory.writerQueue.drainTo(writerMessages)

        assertThat(writerMessages).apply {
            describedAs("Should write a Disconnect message to the Writer")
            contains(Writer.Work.Disconnect())
        }
    }

    @Test
    fun `it should disconnect when receiving disconnect() is called`() {
        val testSubscriber = TestSubscriber<RelayConnectionEvent>()

        val readerWriterFactory = DummyReaderWriterFactory()

        val socketConnector = DummySocketConnector()

        val connectionManager = ConnectionManager(
            socketConnector,
            testSubscriber,
            mock(),
            mock(),
            readerWriterFactory
        )

        connectionManager.disconnect()

        connectionManager.run()

        assertDisconnected(testSubscriber, socketConnector, readerWriterFactory)
    }

    @Test
    fun `it should disconnect when receiving an EOF message`() {
        testDisconnectFromMessage(ConnectionManagerMessage.EOF())
    }

    @Test
    fun `it should disconnect on reader error`() {
        testDisconnectFromMessage(ConnectionManagerMessage.ReaderError(TestException()))
    }

    @Test
    fun `it should disconnect on writer error`() {
        testDisconnectFromMessage(ConnectionManagerMessage.WriterError(TestException()))
    }

    @Test
    fun `it should create Reader and Writer with the proper streams`() {
        val readerWriterFactory = DummyReaderWriterFactory()

        val socketConnector = DummySocketConnector()

        val connectionManager = ConnectionManager(
            socketConnector,
            TestSubscriber(),
            mock(),
            mock(),
            readerWriterFactory
        )

        connectionManager.connect()

        assertEquals(socketConnector.inputStream, readerWriterFactory.readerStream, "Invalid stream given to reader")
        assertEquals(socketConnector.outputStream, readerWriterFactory.writerStream, "Invalid stream given to writer")
    }

    @Test
    fun `it should send a RelayConnectionEstablished message after a successful connection`() {
        val testSubscriber = TestSubscriber<RelayConnectionEvent>()

        val connectionManager = ConnectionManager(
            DummySocketConnector(),
            testSubscriber,
            mock(),
            mock(),
            DummyReaderWriterFactory()
        )

        connectionManager.connect()

        assertThat(testSubscriber.filterEvents<RelayConnectionEstablished>()).apply {
            describedAs("Should emit a RelayConnectionEstablished message")
            haveAtMost(1, cond("RelayConnectionEstablished") {
                it.connection === connectionManager
            })
        }
    }

    @Test
    fun `it should send connection errors across the observable`() {
        val testSubscriber = TestSubscriber<RelayConnectionEvent>()

        val socketConnector = object : SocketConnector {
            override fun connect(): Pair<InputStream, OutputStream> {
                throw TestException()
            }

            override fun disconnect() {
            }
        }

        val connectionManager = ConnectionManager(
            socketConnector,
            testSubscriber,
            mock(),
            mock(),
            mock()
        )

        connectionManager.run()

        assertThat(testSubscriber.onErrorEvents).apply {
            describedAs("Should contain an error")
            contains(TestException())
        }
    }

    @Test
    fun `it should call the ClientMessageHandler for encoding outbound RelayMessages`() {
        val testSubscriber = TestSubscriber<RelayConnectionEvent>()

        val readerWriterFactory = DummyReaderWriterFactory()

        val socketConnector = DummySocketConnector()

        val clientMessageHandler = mock<ClientMessageHandler>()

        val connectionManager = ConnectionManager(
            socketConnector,
            testSubscriber,
            clientMessageHandler,
            mock(),
            readerWriterFactory
        )

        connectionManager.connect()

        val outboundMessage = randomOutboundRelayMessage()
        val bytes = outboundMessage.toByteArray()

        whenever(clientMessageHandler.write(outboundMessage)).thenReturn(bytes)

        connectionManager.sendMessage(outboundMessage)

        connectionManager.processMessages(false)

        assertThat(readerWriterFactory.writerQueue).apply {
            describedAs("Should send data to writer")
            contains(Writer.Work.Write(bytes))
        }
    }

    @Test
    fun `it should call the ServerMessageHandler for decoding inbound relay data`() {
        val testSubscriber = TestSubscriber<RelayConnectionEvent>()

        val readerWriterFactory = DummyReaderWriterFactory()

        val socketConnector = DummySocketConnector()

        val serverMessageHandler = mock<ServerMessageHandler>()

        val connectionManager = ConnectionManager(
            socketConnector,
            testSubscriber,
            mock(),
            serverMessageHandler,
            readerWriterFactory
        )

        val inboundMessage = randomInboundRelayMessage()
        val bytes = inboundMessage.toByteArray()

        connectionManager.connect()

        whenever(serverMessageHandler.decode(bytes)).thenReturn(listOf(inboundMessage))

        readerWriterFactory.messagesQueue.put(ConnectionManagerMessage.ReaderData(bytes))

        connectionManager.processMessages(false)

        val messages = testSubscriber.filterEvents<RelayMessage>()

        assertThat(messages).apply {
            describedAs("Should emit decoded messages")
            containsOnly(inboundMessage)
        }
    }
}