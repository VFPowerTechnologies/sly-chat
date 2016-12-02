package io.slychat.messenger.core.relay.base.java

import java.io.InputStream
import java.io.OutputStream
import java.util.concurrent.BlockingQueue

/** Responsible for spawning readers and writers for the given streams. */
internal interface ReaderWriterFactory {
    fun createReader(
        inputStream: InputStream,
        messages: BlockingQueue<ConnectionManagerMessage>
    )

    fun createWriter(
        outputStream: OutputStream,
        messages: BlockingQueue<ConnectionManagerMessage>,
        writerQueue: BlockingQueue<Writer.Work>
    )
}