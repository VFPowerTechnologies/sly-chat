package io.slychat.messenger.core

import org.spongycastle.crypto.digests.MD5Digest
import java.io.FilterInputStream
import java.io.InputStream

class MD5InputStream(inputStream: InputStream) : FilterInputStream(inputStream) {
    private val digester = MD5Digest()
    private var hasCompleted = false
    private val digest = ByteArray(digester.digestSize)

    override fun read(): Int {
        TODO()
    }

    override fun read(b: ByteArray, off: Int, len: Int): Int {
        val r = `in`.read(b, off, len)

        if (r != -1) {
            digester.update(b, off, r)
        }
        else {
            digester.doFinal(digest, 0)
            hasCompleted = true
        }

        return r
    }

    val digestBytes: ByteArray
        get() {
            if (!hasCompleted)
                error("InputStream has not reached EOF")

            return digest
        }

    val digestString: String
        get() = digestBytes.hexify()

    override fun close() {
        `in`.close()

        //we also check and finish this here since we're not guaranteed to read EOF in certain cases if we close the
        //stream once we've reached a certain amount of data)
        if (!hasCompleted) {
            digester.doFinal(digest, 0)
            hasCompleted = true
        }
    }
}