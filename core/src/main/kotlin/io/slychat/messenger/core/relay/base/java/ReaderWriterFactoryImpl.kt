package io.slychat.messenger.core.relay.base.java

import java.io.InputStream
import java.io.OutputStream
import java.util.concurrent.BlockingQueue

internal class ReaderWriterFactoryImpl : ReaderWriterFactory {
    private fun spawn(runnable: Runnable, name: String) {
        val t = Thread(runnable, name)
        t.isDaemon = true
        t.start()
    }

    override fun createReader(inputStream: InputStream, messages: BlockingQueue<ConnectionManagerMessage>) {
        spawn(Reader(inputStream, messages), "RelayReader")
    }

    override fun createWriter(outputStream: OutputStream, messages: BlockingQueue<ConnectionManagerMessage>, writerQueue: BlockingQueue<Writer.Work>) {
        spawn(Writer(outputStream, messages, writerQueue), "RelayWriter")
    }
}