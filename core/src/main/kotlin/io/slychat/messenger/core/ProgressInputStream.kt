package io.slychat.messenger.core

import java.io.FilterInputStream
import java.io.InputStream

/**
 * Calls a function whenever bytes are read from the input.
 */
class ProgressInputStream(
    inputStream: InputStream,
    private val progressFunction: (Int) -> Unit
) : FilterInputStream(inputStream) {
    override fun read(): Int {
        val c = super.read()

        if (c != -1)
            progressFunction(1)

        return c
    }

    override fun read(b: ByteArray): Int {
        val read = super.read(b)

        if (read > 0)
            progressFunction(read)

        return read
    }

    override fun read(b: ByteArray, off: Int, len: Int): Int {
        val read = super.read(b, off, len)

        if (read > 0)
            progressFunction(read)

        return read
    }
}