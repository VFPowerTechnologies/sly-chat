package io.slychat.messenger.core.crypto

import io.slychat.messenger.core.crypto.ciphers.AES256CBCHMACCipher
import org.assertj.core.api.Assertions.assertThat
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import kotlin.test.assertTrue

class EncryptOutputStreamTest {
    companion object {
        private val cipher = AES256CBCHMACCipher()
        private val key = generateKey(cipher.keySizeBits)
    }

    private fun decrypt(outputStream: ByteArrayOutputStream, chunkSize: Int): ByteArray {
        return DecryptInputStream(cipher, key, ByteArrayInputStream(outputStream.toByteArray()), chunkSize).use {
            it.readBytes()
        }
    }

    private fun assertNotEmpty(outputStream: ByteArrayOutputStream) {
        assertTrue(outputStream.size() > 0, "Nothing written to stream")
    }

    private fun assertCorrectOutput(outputStream: ByteArrayOutputStream, chunkSize: Int, original: ByteArray) {
        assertNotEmpty(outputStream)

        assertThat(decrypt(outputStream, chunkSize)).inHexadecimal().isEqualTo(original)
    }

    private fun runSimpleTest(input: ByteArray, chunkSize: Int) {
        val outputStream = ByteArrayOutputStream()

        EncryptOutputStream(cipher, key, chunkSize, outputStream).use {
            it.write(input)
        }

        assertCorrectOutput(outputStream, chunkSize, input)
    }

    @Test
    fun `write should handle exact chunks`() {
        val chunkSize = 5
        val data = ByteArray(chunkSize)

        runSimpleTest(data, chunkSize)
    }

    @Test
    fun `write should handle multiple event chunks in a single call`() {
        val chunkSize = 5
        val data = ByteArray(chunkSize * 2)

        runSimpleTest(data, chunkSize)
    }

    @Test
    fun `write should work with chunks split across writes`() {
        val chunkSize = 5
        val data = ByteArray(chunkSize)

        val outputStream = ByteArrayOutputStream()

        EncryptOutputStream(cipher, key, chunkSize, outputStream).use {
            it.write(data, 0, 2)
            it.write(data, 2, 3)
        }

        assertCorrectOutput(outputStream, chunkSize, data)
    }

    @Test
    fun `it should write any final uneven chunk when close is called`() {
        val chunkSize = 5
        val data = ByteArray(chunkSize + 2)

        runSimpleTest(data, chunkSize)
    }
}