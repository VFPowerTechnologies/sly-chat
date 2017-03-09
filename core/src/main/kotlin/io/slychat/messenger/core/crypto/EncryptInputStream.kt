package io.slychat.messenger.core.crypto

import io.slychat.messenger.core.crypto.ciphers.Cipher
import io.slychat.messenger.core.crypto.ciphers.Key
import java.io.InputStream

class EncryptInputStream(
    private val cipher: Cipher,
    private val key: Key,
    private val inputStream: InputStream,
    val chunkSize: Int
) : InputStream() {
    private var currentChunk: ByteArray? = null
    private var currentChunkOffset = 0
    private var currentChunkSize = 0

    init {
        require(chunkSize >= 0) { "chunkSize must be >= 0, got $chunkSize" }
    }

    //never used
    override fun read(): Int {
        TODO()
    }

    override fun read(b: ByteArray, off: Int, len: Int): Int {
        var read = 0

        var remainingSize = len

        while (remainingSize > 0) {
            val chunk = currentChunk ?: nextChunk() ?: break

            val remainingChunkSize = currentChunkSize - currentChunkOffset
            if (remainingChunkSize == 0) {
                resetChunk()
                continue
            }

            val length = Math.min(remainingChunkSize, remainingSize)

            System.arraycopy(
                chunk,
                currentChunkOffset,
                b,
                read + off,
                length
            )

            currentChunkOffset += length
            read += length
            remainingSize -= length
        }

        //need to signal EOF
        return if (read == 0) -1 else read
    }

    override fun read(b: ByteArray): Int {
        return read(b, 0, b.size)
    }

    private fun nextChunk(): ByteArray? {
        val chunk = ByteArray(chunkSize)

        currentChunkOffset = 0
        currentChunkSize = inputStream.read(chunk)

        //returns -1 on EOF
        if (currentChunkSize <= 0) {
            currentChunkSize = 0
            currentChunk = null
            return null
        }

        val encrypted = cipher.encrypt(key, chunk, currentChunkSize)

        currentChunk = encrypted
        currentChunkSize = encrypted.size

        return encrypted
    }

    private fun resetChunk() {
        currentChunk = null
        currentChunkOffset = 0
        currentChunkSize = 0
    }

    override fun close() {
        inputStream.close()
        resetChunk()
    }
}