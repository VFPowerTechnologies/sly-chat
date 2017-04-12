package io.slychat.messenger.core.crypto.ciphers

import io.slychat.messenger.core.crypto.getRandomBits
import org.spongycastle.crypto.CipherParameters
import org.spongycastle.crypto.InvalidCipherTextException
import org.spongycastle.crypto.digests.SHA256Digest
import org.spongycastle.crypto.engines.AESFastEngine
import org.spongycastle.crypto.macs.HMac
import org.spongycastle.crypto.modes.CBCBlockCipher
import org.spongycastle.crypto.paddings.PKCS7Padding
import org.spongycastle.crypto.paddings.PaddedBufferedBlockCipher
import org.spongycastle.crypto.params.KeyParameter
import org.spongycastle.crypto.params.ParametersWithIV
import org.spongycastle.util.Arrays

class AES256CBCHMACCipher : Cipher {
    companion object {
        val id: CipherId = CipherId(2)
    }

    override val id: CipherId
        get() = Companion.id

    override val algorithmName: String
        get() = "AES-256-CBC+HMAC"

    //256 for AES, 256 for HMAC-SHA256
    override val keySizeBits: Int
        get() = 512

    private val encKeySizeBits = 256
    private val encKeySizeBytes = encKeySizeBits / 8
    private val macKeySizeBits = 256
    private val macKeySizeBytes = macKeySizeBits / 8

    //AES block size is fixed at 128bits
    private val ivSizeBits = 128
    private val ivSizeBytes = ivSizeBits / 8

    override fun getEncryptedSize(size: Int): Int {
        val key = ByteArray(encKeySizeBytes)

        val iv = ByteArray(ivSizeBytes)

        val cipher = newCipher(true, ParametersWithIV(KeyParameter(key), iv))
        val mac = newMac(ByteArray(macKeySizeBytes))

        return ivSizeBytes + mac.macSize + cipher.getOutputSize(size)
    }

    private fun newCipher(forEncryption: Boolean, params: CipherParameters): PaddedBufferedBlockCipher {
        val cipher = CBCBlockCipher(AESFastEngine())
        //PKCS7 padding by default, but being more explicit
        val paddedCipher = PaddedBufferedBlockCipher(cipher, PKCS7Padding())

        paddedCipher.init(forEncryption, params)

        return paddedCipher
    }

    private fun newMac(macKey: ByteArray): HMac {
        val mac = HMac(SHA256Digest())
        mac.init(KeyParameter(macKey))

        return mac
    }

    /** Returns (encKey, macKey) */
    private fun splitKey(key: Key): Pair<ByteArray, ByteArray> {
        val encKey = ByteArray(encKeySizeBytes)
        System.arraycopy(key.raw, 0, encKey, 0, encKey.size)

        val macKey = ByteArray(macKeySizeBytes)
        System.arraycopy(key.raw, encKey.size, macKey, 0, macKey.size)

        return encKey to macKey
    }

    private fun encrypt(paddedCipher: PaddedBufferedBlockCipher, input: ByteArray, inputSize: Int, output: ByteArray, outputOffset: Int) {
        require(outputOffset >= 0) { "outputOffset must be >= 0, got $outputOffset" }

        val outputLength = paddedCipher.processBytes(input, 0, inputSize, output, outputOffset)
        paddedCipher.doFinal(output, outputOffset + outputLength)
    }

    private fun decrypt(paddedCipher: PaddedBufferedBlockCipher, input: ByteArray, inputOffset: Int, inputSize: Int): ByteArray {
        require(inputOffset > 0) { "inputOffset must be > 0, got $inputOffset" }
        require(inputSize > 0) { "inputSize must be > 0, got $inputSize" }

        val output = ByteArray(paddedCipher.getOutputSize(inputSize))

        var outputLength = paddedCipher.processBytes(input, inputOffset, inputSize, output, 0)
        outputLength += paddedCipher.doFinal(output, outputLength)

        return if (outputLength < output.size) {
            val noPadding = ByteArray(outputLength)
            System.arraycopy(output, 0, noPadding, 0, outputLength)
            noPadding
        }
        else
            output
    }

    override fun encrypt(key: Key, plaintext: ByteArray): ByteArray {
        return encrypt(key, plaintext, plaintext.size)
    }

    override fun encrypt(key: Key, plaintext: ByteArray, plaintextSize: Int): ByteArray {
        //encrypted format is: MAC | IV | Ciphertext
        //where MAC covers the IV and Ciphertext
        val (encKey, macKey) = splitKey(key)
        val iv = getRandomBits(ivSizeBits)

        val cipher = newCipher(true, ParametersWithIV(KeyParameter(encKey), iv))
        val hmac = newMac(macKey)

        val ivOffset = hmac.macSize
        val ciphertextOffset = hmac.macSize + iv.size
        val cipherTextSize = cipher.getOutputSize(plaintextSize)
        val outputSize = ciphertextOffset + cipherTextSize
        val authSize = ivSizeBytes + cipherTextSize

        //prepend MAC, then IV
        val output = ByteArray(outputSize)

        System.arraycopy(
            iv,
            0,
            output,
            hmac.macSize,
            iv.size
        )

        encrypt(cipher, plaintext, plaintextSize, output, ciphertextOffset)

        hmac.update(output, ivOffset, authSize)

        hmac.doFinal(output, 0)

        return output
    }

    override fun decrypt(key: Key, ciphertext: ByteArray): ByteArray {
        return decrypt(key, ciphertext, ciphertext.size)
    }

    override fun decrypt(key: Key, ciphertext: ByteArray, ciphertextSize: Int): ByteArray {
        val (encKey, macKey) = splitKey(key)
        val hmac = newMac(macKey)

        val minSize = hmac.macSize + ivSizeBytes

        if (ciphertextSize < minSize)
            throw MalformedEncryptedDataException("Malformed encrypted data")

        val ivOffset = hmac.macSize
        val ciphertextOffset = hmac.macSize + ivSizeBytes
        val authSize = ciphertextSize - hmac.macSize
        val actualCiphertextSize = ciphertextSize - (hmac.macSize + ivSizeBytes)

        val expectedMac = ciphertext.copyOfRange(0, hmac.macSize)
        //authenticate
        hmac.update(ciphertext, ivOffset, authSize)

        val calcedMac = ByteArray(hmac.macSize)
        hmac.doFinal(calcedMac, 0)

        if (!Arrays.constantTimeAreEqual(expectedMac, calcedMac))
            throw InvalidCipherTextException()

        val iv = ciphertext.copyOfRange(ivOffset, ivOffset + ivSizeBytes)

        val cipher = newCipher(false, ParametersWithIV(KeyParameter(encKey), iv))

        return decrypt(cipher, ciphertext, ciphertextOffset, actualCiphertextSize)
    }
}