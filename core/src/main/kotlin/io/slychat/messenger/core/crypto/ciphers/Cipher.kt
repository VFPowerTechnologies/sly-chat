package io.slychat.messenger.core.crypto.ciphers

/**
 * High-level encryption cipher. Handles IV generation and prepending it to the encrypted data for use during encryption.
 *
 * As this is usually used as a singleton, it must NOT contain any state, as it can be called from multiple threads simulatenously.
 */
interface Cipher {
    /** Uniquely IDs this Cipher within the application. */
    val id: CipherId

    /** Algorithm name as a string. */
    val algorithmName: String

    /** Size of key this cipher expects as input. */
    val keySizeBits: Int

    /** Encrypt data. */
    fun encrypt(key: Key, plaintext: ByteArray): ByteArray

    /** Encrypt data using only the first [plaintextSize] bytes of [plaintext]. */
    fun encrypt(key: Key, plaintext: ByteArray, plaintextSize: Int): ByteArray

    /**
     * Decrypts the given data, using the entirety of the [ciphertext].
     *
     * @throws MalformedEncryptedDataException If data is not in expected format (eg: missing IV or hash)
     * @throws org.spongycastle.crypto.InvalidCipherTextException Decryption of data failed.
     */
    fun decrypt(key: Key, ciphertext: ByteArray): ByteArray

    /**
     * Decrypts the given data, using only the first [ciphertextSize] bytes of [ciphertext].
     *
     * @throws MalformedEncryptedDataException If data is not in expected format (eg: missing IV or hash)
     * @throws org.spongycastle.crypto.InvalidCipherTextException Decryption of data failed.
     */
    fun decrypt(key: Key, ciphertext: ByteArray, ciphertextSize: Int): ByteArray

    /** Returns the expected size of encrypting data of the given size. This includes the prepended IV's size as well. */
    fun getEncryptedSize(size: Int): Int
}