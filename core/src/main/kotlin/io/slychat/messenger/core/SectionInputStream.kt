package io.slychat.messenger.core

import java.io.FilterInputStream
import java.io.InputStream

class SectionInputStream(
    inputStream: InputStream,
    offset: Long,
    size: Long
) : FilterInputStream(inputStream) {
    private var remaining = size

    init {
        `in`.skip(offset)
    }

    override fun read(): Int {
        TODO()
    }

    override fun read(b: ByteArray, off: Int, len: Int): Int {
        if (remaining <= 0)
            return -1

        val toRead = Math.min(b.size.toLong(), remaining)
        val read = `in`.read(b, off, toRead.toInt())

        if (read > 0)
            remaining -= read

        return read
    }

    override fun read(b: ByteArray): Int {
        return read(b, 0, b.size)
    }
}