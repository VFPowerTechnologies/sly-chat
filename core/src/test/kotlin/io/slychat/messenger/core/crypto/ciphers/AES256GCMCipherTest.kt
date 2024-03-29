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
}