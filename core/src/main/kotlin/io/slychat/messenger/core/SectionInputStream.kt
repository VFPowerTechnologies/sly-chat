package io.slychat.messenger.core

import java.io.InputStream

class SectionInputStream(
    private val inputStream: InputStream,
    offset: Long,
    size: Long
) : InputStream() {
    private var remaining = size

    init {
        inputStream.skip(offset)
    }

    override fun read(): Int {
        TODO()
    }

    override fun read(b: ByteArray, off: Int, len: Int): Int {
        if (remaining <= 0)
            return -1

        val toRead = Math.min(b.size.toLong(), remaining)
        val read = inputStream.read(b, off, toRead.toInt())

        if (read > 0)
            remaining -= read

        return read
    }

    override fun read(b: ByteArray): Int {
        return read(b, 0, b.size)
    }

    override fun close() {
        inputStream.close()
    }
}