package io.slychat.messenger.core.crypto.ciphers

interface Cipher {
    val id: CipherId

    val algorithmName: String

    val keySizeBits: Int

    fun encrypt(key: ByteArray, plaintext: ByteArray): ByteArray
    fun decrypt(key: ByteArray, ciphertext: ByteArray): ByteArray
}