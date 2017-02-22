package com.vfpowertech.httpuploader

import java.io.InputStream

class TestInputStream(
    vararg val data: ByteArray
) : InputStream() {
    private var currentDataIndex = 0
    private var currentRead = 0

    override fun read(): Int {
        TODO()
    }

    override fun read(b: ByteArray, off: Int, len: Int): Int {
        if (currentDataIndex >= data.size)
            return -1

        val bytes = data[currentDataIndex]

        val remaining = bytes.size - currentRead
        if (remaining == 0)  {
            currentDataIndex += 1
            currentRead = 0
            return 0
        }

        val toCopy = Math.min(len, remaining)
        System.arraycopy(
            bytes,
            currentRead,
            b,
            off,
            toCopy
        )

        currentRead += toCopy

        return toCopy
    }

    override fun read(b: ByteArray): Int {
        return read(b, 0, b.size)
    }
}