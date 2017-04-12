package io.slychat.messenger.core.crypto.ciphers

import io.slychat.messenger.core.crypto.generateKey
import org.junit.Assert.assertEquals
import org.junit.Test
import org.spongycastle.crypto.InvalidCipherTextException
import kotlin.test.assertFailsWith

class AES256CBCHMACCipherTest {
    private val plaintext = "test"

    @Test
    fun `it should be able to encrypt and decrypt`() {
        val cipher = AES256CBCHMACCipher()

        val key = generateKey(cipher.keySizeBits)

        val ciphertext = cipher.encrypt(key, plaintext.toByteArray(Charsets.UTF_8))

        val decrypted = cipher.decrypt(key, ciphertext)

        assertEquals(plaintext, decrypted.toString(Charsets.UTF_8))
    }

    private fun testModificationFailure(body: (ByteArray) -> Unit) {
        val cipher = AES256CBCHMACCipher()

        val key = generateKey(cipher.keySizeBits)

        val ciphertext = cipher.encrypt(key, plaintext.toByteArray(Charsets.UTF_8))

        body(ciphertext)

        assertFailsWith(InvalidCipherTextException::class) {
            cipher.decrypt(key, ciphertext)
        }
    }

    @Test
    fun `it should throw when the ciphertext is extended`() {
        val cipher = AES256CBCHMACCipher()

        val key = generateKey(cipher.keySizeBits)

        val ciphertext = cipher.encrypt(key, plaintext.toByteArray(Charsets.UTF_8))

        val modified = ciphertext + byteArrayOf(0x55)

        assertFailsWith(InvalidCipherTextException::class) {
            cipher.decrypt(key, modified)
        }
    }

    @Test
    fun `it should throw when the ciphertext is truncated`() {
        val cipher = AES256CBCHMACCipher()

        val key = generateKey(cipher.keySizeBits)

        val ciphertext = cipher.encrypt(key, plaintext.toByteArray(Charsets.UTF_8))

        val modified = ciphertext.copyOfRange(0, ciphertext.size - 1)

        assertFailsWith(InvalidCipherTextException::class) {
            cipher.decrypt(key, modified)
        }
    }

    @Test
    fun `it should throw when the ciphertext is modified`() {
        testModificationFailure {
            it[it.size - 1] = -1
            it[it.size - 2] = -1

        }
    }

    @Test
    fun `it should throw when the iv is modified`() {
        testModificationFailure {
            it[32] = -1
            it[33] = -1
        }
    }

    @Test
    fun `it should thrown when the mac is modified`() {
        testModificationFailure {
            it[0] = -1
            it[1] = -1
        }
    }
}
