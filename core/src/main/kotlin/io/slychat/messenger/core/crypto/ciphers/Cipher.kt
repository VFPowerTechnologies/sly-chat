package io.slychat.messenger.core.crypto.ciphers

interface Cipher {
    val id: CipherId

    val algorithmName: String

    val keySizeBits: Int

    fun encrypt(key: Key, plaintext: ByteArray): ByteArray

    fun encrypt(key: Key, plaintext: ByteArray, plaintextSize: Int): ByteArray

    fun decrypt(key: Key, ciphertext: ByteArray): ByteArray

    fun decrypt(key: Key, ciphertext: ByteArray, ciphertextSize: Int): ByteArray

    /** Returns the expected size of encrypting data of the given size. This includes the prepended IV's size as well. */
    fun getEncryptedSize(size: Int): Int
}