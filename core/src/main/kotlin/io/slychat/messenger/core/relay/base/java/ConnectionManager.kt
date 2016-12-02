package io.slychat.messenger.core.relay.base.java

import io.slychat.messenger.core.condError
import io.slychat.messenger.core.isNotNetworkError
import io.slychat.messenger.core.relay.base.*
import org.slf4j.LoggerFactory
import rx.Observer
import java.util.concurrent.ArrayBlockingQueue

internal class ConnectionManager(
    private val socketConnector: SocketConnector,
    private val observer: Observer<in RelayConnectionEvent>,
    private val clientMessageHandler: ClientMessageHandler,
    private val serverMessageHandler: ServerMessageHandler,
    private val readerWriterFactory: ReaderWriterFactory
) : Runnable, RelayConnection {
    private val log = LoggerFactory.getLogger(javaClass)

    //just for tests
    private var initialized = false

    private val writerQueue = ArrayBlockingQueue<Writer.Work>(100)
    private val messages = ArrayBlockingQueue<ConnectionManagerMessage>(100)

    override fun run() {
        try {
            connect()
            processMessages(true)
        }
        catch (t: Throwable) {
            observer.onError(t)
            doDisconnect()
        }
    }

    internal fun connect() {
        log.debug("Connecting to relay")

        val (inputStream, outputStream) = socketConnector.connect()

        log.debug("Connected, spawning reader/writer workers")

        readerWriterFactory.createReader(inputStream, messages)
        readerWriterFactory.createWriter(outputStream, messages, writerQueue)

        observer.onNext(RelayConnectionEstablished(this))

        initialized = true
    }

    private fun nextMessage(block: Boolean): ConnectionManagerMessage? {
        return if (block)
            messages.take()
        else
            messages.poll()
    }

    internal fun processMessages(block: Boolean) {
        assert(initialized) { "processMessages() called before connect()" }

        log.debug("Enter message loop")
        try {
            var keepRunning = true

            while (keepRunning) {
                val v = nextMessage(block) ?: if (block)
                    continue
                else
                    return

                keepRunning = handleMessage(v)
            }
        }
        finally {
            doDisconnect()
        }

    }

    private fun handleMessage(v: ConnectionManagerMessage): Boolean {
        return when (v) {
            is ConnectionManagerMessage.Disconnect -> {
                log.debug("Disconnect requested")
                false
            }

            is ConnectionManagerMessage.WriterError -> {
                val t = v.throwable
                log.condError(isNotNetworkError(t), "Writer error: {}", t.message, t)
                false
            }

            is ConnectionManagerMessage.ReaderError -> {
                val t = v.throwable
                log.condError(isNotNetworkError(t), "Reader error: {}", t.message, t)
                false
            }

            is ConnectionManagerMessage.ReaderData -> {
                serverMessageHandler.decode(v.bytes).forEach { observer.onNext(it) }
                true
            }

            is ConnectionManagerMessage.EOF -> {
                log.debug("EOF received")
                false
            }
        }
    }

    private fun doDisconnect() {
        socketConnector.disconnect()
        writerQueue.put(Writer.Work.Disconnect())
        observer.onNext(RelayConnectionLost())
    }

    override fun sendMessage(message: RelayMessage) {
        val data = clientMessageHandler.write(message)
        writerQueue.put(Writer.Work.Write(data))
    }

    override fun disconnect() {
        messages.put(ConnectionManagerMessage.Disconnect())
    }
}