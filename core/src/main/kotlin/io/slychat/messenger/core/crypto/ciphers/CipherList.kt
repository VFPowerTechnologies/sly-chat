package io.slychat.messenger.core.crypto.ciphers

import java.util.*

/** List of known ciphers for use in bulk data encryption. */
object CipherList {
    private val ciphers = HashMap<CipherId, Cipher>()

    init {
        registerCipher(AES256GCMCipher())
        registerCipher(AES256CBCHMACCipher())
    }

    private fun registerCipher(cipher: Cipher) {
        val existing = ciphers[cipher.id]

        if (existing != null)
            throw IllegalStateException("Duplicate cipher id detected: ${cipher.id} (${existing.algorithmName} and ${cipher.algorithmName})")

        ciphers[cipher.id] = cipher
    }

    val defaultDataEncryptionCipher: Cipher
        get() = getCipher(AES256CBCHMACCipher.id)

    fun getCipher(cipherId: CipherId): Cipher {
        return ciphers[cipherId] ?: throw UnknownCipherException(cipherId)
    }
}