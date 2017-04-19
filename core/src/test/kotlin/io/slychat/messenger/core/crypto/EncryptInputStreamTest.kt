package io.slychat.messenger.core.crypto

import com.nhaarman.mockito_kotlin.mock
import com.nhaarman.mockito_kotlin.verify
import io.slychat.messenger.core.crypto.ciphers.CipherList
import io.slychat.messenger.core.crypto.ciphers.Key
import org.junit.BeforeClass
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.InputStream
import java.util.*
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class EncryptInputStreamTest {
    companion object {
        private val cipher = CipherList.defaultDataEncryptionCipher
        private lateinit var key: Key

        @JvmStatic
        @BeforeClass
        fun beforeClass() {
            key = Key(getRandomBits(cipher.keySizeBits))
        }
    }

    private fun getSingleBlockSize(blockSize: Int): Int {
        return cipher.getEncryptedSize(blockSize)
    }

    private fun assertByteArraysEqual(expected: ByteArray, actual: ByteArray) {
        assertTrue(Arrays.equals(expected, actual), "Expected ${Arrays.toString(expected)} but got ${Arrays.toString(actual)}")
    }

    private fun newCipherStream(input: ByteArray, chunkSize: Int): EncryptInputStream {
        val inputStream = ByteArrayInputStream(input)
        return EncryptInputStream(cipher, key, inputStream, chunkSize)
    }

    @Test
    fun `it should encrypt input when chunkSize == inputSize`() {
        val plaintext = ByteArray(10)
        val cipherStream = newCipherStream(plaintext, plaintext.size)

        val ciphertext = ByteArray(getSingleBlockSize(plaintext.size))

        cipherStream.read(ciphertext)

        val decrypted = cipher.decrypt(key, ciphertext)

        assertByteArraysEqual(plaintext, decrypted)
    }

    @Test
    fun `it should properly chunk input`() {
        val chunkSize = 5
        val plaintext = ByteArray(10)
        val cipherStream = newCipherStream(plaintext, chunkSize)

        val encryptedChunkSize = getSingleBlockSize(chunkSize)

        val ciphertext = ByteArray(encryptedChunkSize * 2)

        cipherStream.read(ciphertext)

        val decryptedFirst = cipher.decrypt(key, Arrays.copyOfRange(ciphertext, 0, encryptedChunkSize), encryptedChunkSize)
        val decryptedSecond = cipher.decrypt(key, Arrays.copyOfRange(ciphertext, encryptedChunkSize, ciphertext.size), encryptedChunkSize)

        val decrypted = ByteArray(plaintext.size)
        System.arraycopy(
            decrypted,
            0,
            decryptedFirst,
            0,
            decryptedFirst.size
        )

        System.arraycopy(
            decrypted,
            5,
            decryptedSecond,
            0,
            decryptedSecond.size
        )

        assertByteArraysEqual(plaintext, decrypted)
    }

    @Test
    fun `it should handle an uneven final chunk`() {
        val chunkSize = 5
        val finalChunkSize = 4
        val plaintext = ByteArray(chunkSize + finalChunkSize)
        val cipherStream = newCipherStream(plaintext, chunkSize)

        val encryptedChunkSize = getSingleBlockSize(chunkSize)
        val lastChunkSize = getSingleBlockSize(finalChunkSize)

        val ciphertext = ByteArray(encryptedChunkSize * 2)

        cipherStream.read(ciphertext)

        val decryptedFirst = cipher.decrypt(key, Arrays.copyOfRange(ciphertext, 0, encryptedChunkSize), encryptedChunkSize)
        val decryptedSecond = cipher.decrypt(key, Arrays.copyOfRange(ciphertext, encryptedChunkSize, ciphertext.size), lastChunkSize)

        val decrypted = ByteArray(plaintext.size)
        System.arraycopy(
            decrypted,
            0,
            decryptedFirst,
            0,
            decryptedFirst.size
        )

        System.arraycopy(
            decrypted,
            5,
            decryptedSecond,
            0,
            decryptedSecond.size
        )

        assertByteArraysEqual(plaintext, decrypted)
    }

    @Test
    fun `it should return the proper amount read`() {
        val plaintext = ByteArray(10)
        val cipherStream = newCipherStream(plaintext, plaintext.size)

        val ciphertext = ByteArray(getSingleBlockSize(plaintext.size))

        assertEquals(ciphertext.size, cipherStream.read(ciphertext), "Amount returned by read() is invalid")
    }

    @Test
    fun `it should cache unread part of chunks`() {
        val plaintext = ByteArray(10)
        val cipherStream = newCipherStream(plaintext, plaintext.size)

        val ciphertext = ByteArray(getSingleBlockSize(plaintext.size))
        val half = ciphertext.size / 2
        val rest = ciphertext.size - half

        cipherStream.read(ciphertext, 0, half)
        cipherStream.read(ciphertext, half, rest)

        val decrypted = cipher.decrypt(key, ciphertext, ciphertext.size)

        assertByteArraysEqual(plaintext, decrypted)
    }

    @Test
    fun `close() should call the underlying InputStream close method`() {
        val inputStream = mock<InputStream>()

        EncryptInputStream(cipher, key, inputStream, 10).use {  }
        verify(inputStream).close()
    }
}