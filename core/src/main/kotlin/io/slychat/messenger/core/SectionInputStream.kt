package io.slychat.messenger.core

import java.io.InputStream
import java.io.RandomAccessFile

class SectionInputStream(
    private val randomAccessFile: RandomAccessFile,
    offset: Long,
    size: Long
) : InputStream() {
    private var remaining = size

    init {
        randomAccessFile.seek(offset)
    }

    override fun read(): Int {
        TODO()
    }

    override fun read(b: ByteArray, off: Int, len: Int): Int {
        if (remaining <= 0)
            return -1

        val toRead = Math.min(b.size.toLong(), remaining)
        val read = randomAccessFile.read(b, off, toRead.toInt())

        if (read > 0)
            remaining -= read

        return read
    }

    override fun read(b: ByteArray): Int {
        return read(b, 0, b.size)
    }

    override fun close() {
        randomAccessFile.close()
    }
}