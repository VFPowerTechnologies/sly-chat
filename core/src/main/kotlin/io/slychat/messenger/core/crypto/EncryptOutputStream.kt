package io.slychat.messenger.core.crypto

import io.slychat.messenger.core.crypto.ciphers.Cipher
import io.slychat.messenger.core.crypto.ciphers.Key
import java.io.OutputStream

/** Encrypts written data and writes it to the given [OutputStream]. */
class EncryptOutputStream(
    private val cipher: Cipher,
    private val key: Key,
    private val chunkSize: Int,
    private val outputStream: OutputStream
) : OutputStream() {
    private var currentChunk: ByteArray? = null
    private var currentChunkOffset = 0

    init {
        require(chunkSize >= 0) { "chunkSize must be >= 0, got $chunkSize" }
    }

    override fun write(b: Int) {
        //unused
        TODO()
    }

    private fun getChunk(): ByteArray {
        val chunk = currentChunk

        return if (chunk != null)
            chunk
        else {
            val c = ByteArray(chunkSize)
            currentChunk = c
            c
        }
    }

    private fun resetChunk() {
        currentChunk = null
        currentChunkOffset = 0
    }

    //make sure to handle EOF
    override fun write(b: ByteArray, off: Int, len: Int) {
        var remainingSize = len
        var wrote = 0

        while (remainingSize > 0) {
            val chunk = getChunk()

            val remainingChunkSize = chunkSize - currentChunkOffset

            val toCopy = Math.min(remainingChunkSize, remainingSize)

            System.arraycopy(
                b,
                off + wrote,
                chunk,
                currentChunkOffset,
                toCopy
            )

            remainingSize -= toCopy
            wrote += toCopy
            currentChunkOffset += toCopy

            if (currentChunkOffset >= chunkSize) {
                writeChunk(chunk, chunkSize)
                resetChunk()
            }
        }
    }

    private fun writeChunk(chunk: ByteArray, chunkSize: Int) {
        val ciphertext = cipher.encrypt(key, chunk, chunkSize)
        outputStream.write(ciphertext)
    }

    override fun close() {
        flushLastChunk()
        outputStream.close()
    }

    private fun flushLastChunk() {
        val chunk = currentChunk ?: return

        writeChunk(chunk, chunkSize - currentChunkOffset - 1)
    }
}