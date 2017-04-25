package io.slychat.messenger.services.files.cache

import java.io.Closeable
import java.io.InputStream
import java.io.OutputStream

class ThumbnailWriteStreams(val inputStream: InputStream, val outputStream: OutputStream) : Closeable {
    override fun close() {
        inputStream.close()
        outputStream.close()
    }
}