package io.slychat.messenger.core.crypto

import com.vfpowertech.httpuploader.TestInputStream
import io.slychat.messenger.core.crypto.ciphers.CipherList
import io.slychat.messenger.core.crypto.ciphers.Key
import io.slychat.messenger.core.crypto.ciphers.MalformedEncryptedDataException
import org.junit.BeforeClass
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.security.SecureRandom
import java.util.*
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class DecryptInputStreamTest {
    companion object {
        private lateinit var key: Key

        @JvmStatic
        @BeforeClass
        fun beforeClass() {
            val b = ByteArray(256 / 8)
            SecureRandom().nextBytes(b)
            key = Key(b)
        }
    }

    private val cipher = CipherList.defaultDataEncryptionCipher

    private fun assertByteArraysEqual(expected: ByteArray, actual: ByteArray) {
        assertTrue(Arrays.equals(expected, actual), "Expected ${Arrays.toString(expected)} but got ${Arrays.toString(actual)}")
    }

    private fun getByteArray(size: Int): ByteArray {
        return ByteArray(size, { 0x77 })
    }

    private fun readUntilEOF(inputStream: InputStream, buffer: ByteArray) {
        var soFar = 0
        var remaining = buffer.size

        while (true) {
            val read = inputStream.read(buffer, soFar, remaining)
            if (read == -1)
                break

            soFar += read
            remaining -= soFar
        }
    }

    @Test
    fun `it should decrypt single chunk output`() {
        val plaintext = getByteArray(10)
        val ciphertext = cipher.encrypt(key, plaintext)

        val inputStream = ByteArrayInputStream(ciphertext)

        val got = ByteArray(plaintext.size)

        DecryptInputStream(cipher, key, inputStream, 10).use {
            it.read(got)
        }

        assertByteArraysEqual(plaintext, got)
    }

    @Test
    fun `it should decrypt multiple chunks`() {
        val plaintext = getByteArray(10)

        val chunkSize = 5

        val ciphertextStream = ByteArrayOutputStream()
        ciphertextStream.write(cipher.encrypt(key, plaintext, chunkSize))
        ciphertextStream.write(cipher.encrypt(key, Arrays.copyOfRange(plaintext, chunkSize, plaintext.size), chunkSize))
        val ciphertext = ciphertextStream.toByteArray()

        val inputStream = ByteArrayInputStream(ciphertext)

        val got = ByteArray(plaintext.size)

        DecryptInputStream(cipher, key, inputStream, chunkSize).use {
            it.read(got)
        }

        assertByteArraysEqual(plaintext, got)
    }

    @Test
    fun `it should decrypt properly when final chunk is uneven`() {
        val plaintext = getByteArray(8)

        val chunkSize = 5

        val ciphertextStream = ByteArrayOutputStream()
        ciphertextStream.write(cipher.encrypt(key, plaintext, chunkSize))
        ciphertextStream.write(cipher.encrypt(key, Arrays.copyOfRange(plaintext, chunkSize, plaintext.size), 3))
        val ciphertext = ciphertextStream.toByteArray()

        val inputStream = ByteArrayInputStream(ciphertext)

        val got = ByteArray(plaintext.size)

        DecryptInputStream(cipher, key, inputStream, chunkSize).use {
            readUntilEOF(it, got)
        }

        assertByteArraysEqual(plaintext, got)
    }

    @Test
    fun `read() should return the number of bytes read`() {
        val plaintext = getByteArray(10)

        val chunkSize = 5

        val ciphertextStream = ByteArrayOutputStream()
        ciphertextStream.write(cipher.encrypt(key, plaintext, chunkSize))
        ciphertextStream.write(cipher.encrypt(key, Arrays.copyOfRange(plaintext, chunkSize, plaintext.size), chunkSize))
        val ciphertext = ciphertextStream.toByteArray()

        val inputStream = ByteArrayInputStream(ciphertext)

        val got = ByteArray(plaintext.size)

        val read = DecryptInputStream(cipher, key, inputStream, chunkSize).use {
            it.read(got)
        }

        assertEquals(plaintext.size, read, "Invalid read count returned")
    }

    @Test
    fun `read should return -1 for EOF`() {
        val got = ByteArray(10)
        val read = DecryptInputStream(cipher, key, ByteArrayInputStream(ByteArray(0)), 10).use {
            it.read(got)
        }

        assertEquals(-1, read, "Expected EOF")
    }

    @Test
    fun `it should throw when IV is not fully read and EOF is reached`() {
        val input = TestInputStream(ByteArray(2))
        assertFailsWith(MalformedEncryptedDataException::class) {
            val got = ByteArray(10)
            DecryptInputStream(cipher, key, input, 10).use {
                while (it.read(got) == 0) {}
            }
        }
    }

    @Test
    fun `it should read chunks across multiple calls`() {
        val plaintext = getByteArray(10)
        val chunkSize = 10
        val ciphertext = cipher.encrypt(key, plaintext, plaintext.size)
        val half = ciphertext.size / 2

        val inputStream = TestInputStream(
            ciphertext.copyOfRange(0, half),
            ciphertext.copyOfRange(half, ciphertext.size)
        )

        val got = ByteArray(plaintext.size)

        DecryptInputStream(cipher, key, inputStream, chunkSize).use {
            var read = 0
            var remaining = got.size
            while (read != plaintext.size) {
                read += it.read(got, read, remaining)
                remaining -= read
            }
        }

        assertByteArraysEqual(plaintext, got)
    }

    @Test
    fun `it should only copy part of the chunk if buffer is too small`() {
        val plaintext = getByteArray(10)
        val chunkSize = 10
        val ciphertext = cipher.encrypt(key, plaintext, plaintext.size)

        val first = ByteArray(5)
        val second = ByteArray(5)

        DecryptInputStream(cipher, key, ByteArrayInputStream(ciphertext), chunkSize).use {
            it.read(first, 0, 5)
            it.read(second, 0, 5)
        }

        val out = ByteArrayOutputStream()
        out.write(first)
        out.write(second)

        assertByteArraysEqual(plaintext, out.toByteArray())
    }

    @Test
    fun `calling read after hitting EOF should return -1`() {
        val got = ByteArray(10)
        val read = DecryptInputStream(cipher, key, ByteArrayInputStream(ByteArray(0)), 10).use {
            it.read(got)
            it.read(got)
        }

        assertEquals(-1, read, "Expected EOF")
    }
}