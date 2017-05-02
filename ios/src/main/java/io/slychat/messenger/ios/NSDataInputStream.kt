package io.slychat.messenger.ios

import apple.foundation.NSData
import java.io.InputStream

/** Equiv of a ByteArrayInputStream over NSData. */
//XXX not sure if the BytePtr stays alive independent of the NSData, so just hang on to it
class NSDataInputStream(private val nsData: NSData) : InputStream() {
    private var position = 0
    private var remaining = nsData.length()

    private val bytesPtr = nsData.bytes().bytePtr

    override fun read(): Int {
        TODO()
    }

    override fun read(b: ByteArray, off: Int, len: Int): Int {
        if (remaining <= 0)
            return -1

        val toCopy = Math.min(len, remaining.toInt())

        bytesPtr.copyTo(position, b, off, toCopy)

        remaining -= toCopy
        position += toCopy

        return toCopy
    }

    override fun skip(n: Long): Long {
        val length = nsData.length()
        val toPos = Math.min(position + n, length).toInt()
        val actualSkipped = toPos - position

        position = toPos
        remaining = length - position

        return actualSkipped.toLong()
    }
}
