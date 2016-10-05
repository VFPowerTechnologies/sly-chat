package io.slychat.messenger.core.crypto.ciphers

interface Cipher {
    val id: CipherId

    val algorithmName: String

    val keySizeBits: Int

    fun encrypt(key: Key, plaintext: ByteArray): ByteArray
    fun decrypt(key: Key, ciphertext: ByteArray): ByteArray
}