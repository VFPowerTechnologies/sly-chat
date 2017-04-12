package io.slychat.messenger.core.integration.crypto

import io.slychat.messenger.core.crypto.DecryptInputStream
import io.slychat.messenger.core.crypto.EncryptInputStream
import io.slychat.messenger.core.crypto.ciphers.AES256CBCHMACCipher
import io.slychat.messenger.core.crypto.ciphers.AES256GCMCipher
import io.slychat.messenger.core.crypto.ciphers.Cipher
import io.slychat.messenger.core.crypto.ciphers.Key
import io.slychat.messenger.core.crypto.getRandomBits
import org.junit.Test
import java.io.InputStream
import kotlin.test.assertEquals

class StreamTest {
    private class DummyInputStream(private val fileSize: Long, private val value: Byte = 0x01) : InputStream() {
        private var totalRead = 0L

        override fun read(): Int {
            if (totalRead >= fileSize)
                return -1

            totalRead += 1
            return value.toInt()
        }
    }

    private fun runTest(cipher: Cipher) {
        val key = Key(getRandomBits(cipher.keySizeBits))
        val chunkSize = 17
        val fileSize = 2L

        EncryptInputStream(cipher, key, DummyInputStream(fileSize), chunkSize).use { inputStream ->
            DecryptInputStream(cipher, key, inputStream, chunkSize).use {
                assertEquals(fileSize, it.readBytes().size.toLong(), "Read incorrect amount of data")
            }
        }
    }

    @Test
    fun `EncryptInputStream and DecryptInputStream should encrypt and decrypt a file (AES-GCM)`() {
        runTest(AES256GCMCipher())
    }

    @Test
    fun `EncryptInputStream and DecryptInputStream should encrypt and decrypt a file (AES-CBC)`() {
        runTest(AES256CBCHMACCipher())
    }
}