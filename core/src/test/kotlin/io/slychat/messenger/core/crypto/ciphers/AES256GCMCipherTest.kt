package io.slychat.messenger.core.crypto.ciphers

import io.slychat.messenger.core.crypto.generateKey
import org.junit.Test
import kotlin.test.assertEquals

class AES256GCMCipherTest {
    @Test
    fun `it should be able to encrypt and decrypt`() {
        val cipher = AES256GCMCipher()

        val key = generateKey(cipher.keySizeBits)

        val plaintext = "test"

        val ciphertext = cipher.encrypt(key, plaintext.toByteArray(Charsets.UTF_8))

        val decrypted = cipher.decrypt(key, ciphertext)

        assertEquals(plaintext, decrypted.toString(Charsets.UTF_8))
    }

    @Test
    fun `it should be able to encrypt and decrypt when given an explicit size`() {
        val cipher = AES256GCMCipher()

        val key = generateKey(cipher.keySizeBits)

        val expected = "test"
        val plaintext = expected + "1"

        val ciphertext = cipher.encrypt(key, plaintext.toByteArray(Charsets.US_ASCII), expected.length)

        val decrypted = cipher.decrypt(key, ciphertext)

        assertEquals(expected, decrypted.toString(Charsets.US_ASCII))
    }

    @Test
    fun `getEncryptedSize should return the proper size`() {
        val cipher = AES256GCMCipher()

        val size = 100
        //+ ivSize + authTagSize
        assertEquals(size + 12 + 16, cipher.getEncryptedSize(size), "Invalid size")
    }
}