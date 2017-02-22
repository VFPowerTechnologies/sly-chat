package io.slychat.messenger.core

import java.io.FilterOutputStream
import java.io.OutputStream

/**
 * Calls a function whenever bytes are written to the output.
 */
class ProgressOutputStream(
    outputStream: OutputStream,
    private val progressFunction: (Long) -> Unit
) : FilterOutputStream(outputStream) {
    override fun write(b: Int) {
        TODO()
    }

    override fun write(b: ByteArray) {
        out.write(b)
        progressFunction(b.size.toLong())
    }

    override fun write(b: ByteArray, off: Int, len: Int) {
        out.write(b, off, len)
        progressFunction(len.toLong())
    }

    override fun close() {
        out.close()
    }
}

