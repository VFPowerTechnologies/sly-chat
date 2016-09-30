package io.slychat.messenger.core.crypto.ciphers

import io.slychat.messenger.core.crypto.HKDFInfo
import io.slychat.messenger.core.emptyByteArray
import org.spongycastle.crypto.digests.SHA512Digest
import org.spongycastle.crypto.generators.HKDFBytesGenerator
import org.spongycastle.crypto.params.HKDFParameters

fun deriveKey(masterKey: ByteArray, info: HKDFInfo, outputKeySizeBits: Int): ByteArray {
    require(outputKeySizeBits > 0) { "outputKeySize should be >= 0, got $outputKeySizeBits" }

    val hkdf = HKDFBytesGenerator(SHA512Digest())
    val params = HKDFParameters.skipExtractParameters(masterKey, info.raw)
    hkdf.init(params)

    val okm = ByteArray(outputKeySizeBits / 8)

    hkdf.generateBytes(okm, 0, okm.size)

    return okm
}

fun encryptBulkData(masterKey: ByteArray, data: ByteArray, info: HKDFInfo): ByteArray {
    return encryptBulkData(CipherList.defaultDataEncryptionCipher, masterKey, data, info)
}

fun encryptBulkData(cipher: Cipher, derivedKey: ByteArray, data: ByteArray): ByteArray {
    val cipherText = cipher.encrypt(derivedKey, data)

    val output = ByteArray(1 + cipherText.size)
    output[0] = cipher.id.short.toByte()

    System.arraycopy(
        cipherText,
        0,
        output,
        1,
        cipherText.size
    )

    return output
}

fun encryptBulkData(cipher: Cipher, masterKey: ByteArray, data: ByteArray, info: HKDFInfo): ByteArray {
    if (data.isEmpty())
        return emptyByteArray()

    val derivedKey = deriveKey(masterKey, info, cipher.keySizeBits)

    return encryptBulkData(cipher, derivedKey, data)
}

//TODO add a no cipherId variant
fun decryptBulkData(masterKey: ByteArray, ciphertext: ByteArray, info: HKDFInfo): ByteArray {
    if (ciphertext.isEmpty())
        return emptyByteArray()

    if (ciphertext.size <= 0)
        throw IllegalArgumentException("Malformed ciphertext")

    val cipherId = CipherId(ciphertext[0].toShort())

    val cipher = CipherList.getCipher(cipherId)

    val derivedKey = deriveKey(masterKey, info, cipher.keySizeBits)

    return cipher.decrypt(derivedKey, ciphertext.copyOfRange(1, ciphertext.size))
}
