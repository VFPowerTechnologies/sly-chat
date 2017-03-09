package io.slychat.messenger.core.crypto.ciphers

import io.slychat.messenger.core.crypto.getRandomBits
import org.spongycastle.crypto.engines.AESFastEngine
import org.spongycastle.crypto.modes.AEADBlockCipher
import org.spongycastle.crypto.modes.GCMBlockCipher
import org.spongycastle.crypto.params.AEADParameters
import org.spongycastle.crypto.params.KeyParameter

//as this is usually used as a singleton, it must NOT contain any state, as it can be called from multiple threads simulatenously
class AES256GCMCipher : Cipher {
    companion object {
        val id: CipherId = CipherId(1)
    }

    override val algorithmName: String
        get() = "AES-$keySizeBits-GCM"

    override val id: CipherId = Companion.id

    override val keySizeBits: Int = 256

    private val authTagLengthBits = 128
    private val ivSizeBits = 96
    private val ivSizeBytes = ivSizeBits / 8

    private fun newCipher(forEncryption: Boolean, aeadParams: AEADParameters): AEADBlockCipher {
        val cipher = GCMBlockCipher(AESFastEngine())
        cipher.init(forEncryption, aeadParams)
        return cipher
    }

    private fun encrypt(aeadBlockCipher: AEADBlockCipher, input: ByteArray, inputSize: Int, output: ByteArray, outputOffset: Int) {
        require(outputOffset >= 0) { "outputOffset must be >= 0, got $outputOffset" }

        val outputLength = aeadBlockCipher.processBytes(input, 0, inputSize, output, outputOffset)
        aeadBlockCipher.doFinal(output, outputOffset + outputLength)
    }

    private fun decrypt(aeadBlockCipher: AEADBlockCipher, input: ByteArray, inputOffset: Int, inputSize: Int): ByteArray {
        require(inputOffset > 0) { "inputOffset must be > 0, got $inputOffset" }
        require(inputSize > 0) { "inputSize must be > 0, got $inputSize" }

        val output = ByteArray(aeadBlockCipher.getOutputSize(inputSize))

        val outputLength = aeadBlockCipher.processBytes(input, inputOffset, inputSize, output, 0)
        aeadBlockCipher.doFinal(output, outputLength)

        return output
    }

    private fun getAEADParameters(key: Key, iv: ByteArray): AEADParameters {
        val keyParam = KeyParameter(key.raw)

        //this actually does take the size in bits, not bytes; not a bug
        return AEADParameters(keyParam, authTagLengthBits, iv)
    }

    override fun encrypt(key: Key, plaintext: ByteArray): ByteArray {
        return encrypt(key, plaintext, plaintext.size)
    }

    override fun encrypt(key: Key, plaintext: ByteArray, plaintextSize: Int): ByteArray {
        val iv = getRandomBits(ivSizeBits)
        val cipher = newCipher(true, getAEADParameters(key, iv))

        val outputSize = iv.size + cipher.getOutputSize(plaintextSize)

        //prepend IV
        val output = ByteArray(outputSize)

        System.arraycopy(
            iv,
            0,
            output,
            0,
            iv.size
        )

        encrypt(cipher, plaintext, plaintextSize, output, iv.size)

        return output
    }

    override fun decrypt(key: Key, ciphertext: ByteArray): ByteArray {
        return decrypt(key, ciphertext, ciphertext.size)
    }

    override fun decrypt(key: Key, ciphertext: ByteArray, ciphertextSize: Int): ByteArray {
        if (ciphertextSize < ivSizeBytes)
            throw IllegalArgumentException("Malformed encrypted data")

        val iv = ciphertext.copyOfRange(0, ivSizeBytes)

        val cipher = newCipher(false, getAEADParameters(key, iv))

        return decrypt(cipher, ciphertext, iv.size, ciphertextSize - iv.size)
    }

    override fun getEncryptedSize(size: Int): Int {
        require(size > 0) { "size must be > 0, got $size" }

        val key = Key(ByteArray(keySizeBits / 8))

        val iv = ByteArray(ivSizeBytes)

        val cipher = newCipher(true, getAEADParameters(key, iv))

        return cipher.getOutputSize(size) + iv.size
    }
}