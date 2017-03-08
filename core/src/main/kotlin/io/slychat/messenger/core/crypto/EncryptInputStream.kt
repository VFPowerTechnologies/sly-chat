package io.slychat.messenger.core.crypto

import io.slychat.messenger.core.crypto.ciphers.Key
import org.spongycastle.crypto.engines.AESFastEngine
import org.spongycastle.crypto.modes.GCMBlockCipher
import org.spongycastle.crypto.params.AEADParameters
import org.spongycastle.crypto.params.KeyParameter
import java.io.InputStream
import java.security.SecureRandom

internal fun encryptBuffer(key: Key, data: ByteArray, size: Int): ByteArray {
    val authTagLength = 128

    val cipher = GCMBlockCipher(AESFastEngine())

    val iv = ByteArray(96 / 8)
    SecureRandom().nextBytes(iv)

    cipher.init(true, AEADParameters(KeyParameter(key.raw), authTagLength, iv))

    val ciphertext = ByteArray(cipher.getOutputSize(size) + iv.size)

    System.arraycopy(
        iv,
        0,
        ciphertext,
        0,
        iv.size
    )

    val outputLength = cipher.processBytes(data, 0, size, ciphertext, iv.size)
    cipher.doFinal(ciphertext, iv.size + outputLength)

    return ciphertext
}

class EncryptInputStream(
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

        val encrypted = encryptBuffer(key, chunk, currentChunkSize)

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