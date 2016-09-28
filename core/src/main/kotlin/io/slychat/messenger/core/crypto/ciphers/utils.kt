package io.slychat.messenger.core.crypto.ciphers

import org.spongycastle.crypto.engines.AESFastEngine
import org.spongycastle.crypto.modes.AEADBlockCipher
import org.spongycastle.crypto.modes.GCMBlockCipher
import org.spongycastle.crypto.params.AEADParameters
import org.spongycastle.crypto.params.KeyParameter

private fun newGCMCipher(forEncryption: Boolean, params: AESGCMParams, key: ByteArray): AEADBlockCipher {
    val cipher = GCMBlockCipher(AESFastEngine())
    val aeadParams = params.toAEADParameters(key)
    cipher.init(forEncryption, aeadParams)
    return cipher
}

private fun getOutputArrayForCipher(cipher: AEADBlockCipher, input: ByteArray): ByteArray {
    val outputLength = cipher.getOutputSize(input.size)
    return ByteArray(outputLength)
}

private fun AEADBlockCipher.processInput(input: ByteArray): ByteArray {
    val output = getOutputArrayForCipher(this, input)
    val outputLength = processBytes(input, 0, input.size, output, 0)
    doFinal(output, outputLength)
    return output
}

private fun AESGCMParams.toAEADParameters(key: ByteArray): AEADParameters {
    val keyParam = KeyParameter(key)
    return AEADParameters(keyParam, authTagLength, iv)
}

/** Encrypt data with the given parameters. */
fun encryptDataWithParams(encryptionSpec: EncryptionSpec, plaintext: ByteArray): EncryptedData = when (encryptionSpec.params) {
    is AESGCMParams -> {
        val cipher = newGCMCipher(true, encryptionSpec.params, encryptionSpec.key)
        val ciphertext = cipher.processInput(plaintext)

        EncryptedData(ciphertext, encryptionSpec.params)
    }
    else -> throw IllegalArgumentException("Unknown cipher: ${encryptionSpec.params.algorithmName}")
}

/**
 * Decrypt data with the given parameters.
 *
 * @throws InvalidCipherTextException If decryption fails.
 */
fun decryptData(encryptionSpec: EncryptionSpec, ciphertext: ByteArray): ByteArray = when (encryptionSpec.params) {
    is AESGCMParams -> {
        val cipher = newGCMCipher(false, encryptionSpec.params, encryptionSpec.key)
        cipher.processInput(ciphertext)
    }
    else -> throw IllegalArgumentException("Unknown cipher: ${encryptionSpec.params.algorithmName}")
}