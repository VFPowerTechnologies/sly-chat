package io.slychat.messenger.ios

import apple.foundation.NSFileHandle
import java.io.InputStream

class NSFileHandleInputStream(private var handle: NSFileHandle) : InputStream() {
    override fun read(): Int {
        TODO()
    }

    override fun read(b: ByteArray, off: Int, len: Int): Int {
        val data = handle.readDataOfLength(len.toLong())

        //EOF
        val bytesRead = data.length().toInt()
        if (bytesRead == 0)
            return -1

        val voidPtr = data.bytes()
        val bytesPtr = voidPtr.bytePtr

        bytesPtr.copyTo(0, b, off, bytesRead)

        return bytesRead
    }

    override fun skip(n: Long): Long {
        val current = handle.offsetInFile()

        handle.seekToFileOffset(current + n)

        return handle.offsetInFile() - current
    }

    override fun close() {
        handle.closeFile()
    }
}
