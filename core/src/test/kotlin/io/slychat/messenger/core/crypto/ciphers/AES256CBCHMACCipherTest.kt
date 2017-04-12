package io.slychat.messenger.core.crypto.ciphers

import io.slychat.messenger.core.crypto.generateKey
import org.junit.Assert.assertEquals
import org.junit.Test
import org.spongycastle.crypto.InvalidCipherTextException
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class AES256CBCHMACCipherTest {
    private val plaintext = "test"
    private val plaintextBytes = plaintext.toByteArray(Charsets.US_ASCII)

    @Test
    fun `it should be able to encrypt and decrypt`() {
        val cipher = AES256CBCHMACCipher()

        val key = generateKey(cipher.keySizeBits)

        val ciphertext = cipher.encrypt(key, plaintextBytes)

        val decrypted = cipher.decrypt(key, ciphertext)

        assertEquals(plaintext, decrypted.toString(Charsets.US_ASCII))
    }

    @Test
    fun `it should be able to encrypt when given an explicit size`() {
        val cipher = AES256CBCHMACCipher()

        val key = generateKey(cipher.keySizeBits)

        val expected = "test"
        //we want to have extra so that it would result in more blocks if not ignored
        val plaintext = expected + "1".repeat(40)

        val ciphertext = cipher.encrypt(key, plaintext.toByteArray(Charsets.US_ASCII), expected.length)

        val decrypted = cipher.decrypt(key, ciphertext)

        assertEquals(expected, decrypted.toString(Charsets.US_ASCII))
    }

    @Test
    fun `it should be able to decrypt when given an explicit size`() {
        val cipher = AES256CBCHMACCipher()

        val key = generateKey(cipher.keySizeBits)

        val ciphertext = cipher.encrypt(key, plaintextBytes)

        val extended = ciphertext + byteArrayOf(0x22)

        val decrypted = cipher.decrypt(key, extended, ciphertext.size)

        assertEquals(plaintext, decrypted.toString(Charsets.US_ASCII))
    }

    private fun testModificationFailure(body: (ByteArray) -> Unit) {
        val cipher = AES256CBCHMACCipher()

        val key = generateKey(cipher.keySizeBits)

        val ciphertext = cipher.encrypt(key, plaintextBytes)

        body(ciphertext)

        assertFailsWith(InvalidCipherTextException::class) {
            cipher.decrypt(key, ciphertext)
        }
    }

    @Test
    fun `it should throw when the ciphertext is extended`() {
        val cipher = AES256CBCHMACCipher()

        val key = generateKey(cipher.keySizeBits)

        val ciphertext = cipher.encrypt(key, plaintextBytes)

        val modified = ciphertext + byteArrayOf(0x55)

        assertFailsWith(InvalidCipherTextException::class) {
            cipher.decrypt(key, modified)
        }
    }

    @Test
    fun `it should throw when the ciphertext is truncated`() {
        val cipher = AES256CBCHMACCipher()

        val key = generateKey(cipher.keySizeBits)

        val ciphertext = cipher.encrypt(key, plaintextBytes)

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

    @Test
    fun `getEncryptedSize should return the proper size`() {
        val cipher = AES256CBCHMACCipher()

        val size = 100
        //+ ivSize + authTagSize
        assertEquals((7 * 16) + 16 + 32, cipher.getEncryptedSize(size), "Invalid size")
    }
}
