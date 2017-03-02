package io.slychat.messenger.core.crypto.ciphers

import io.slychat.messenger.core.crypto.DerivedKeySpec
import io.slychat.messenger.core.crypto.HKDFInfo
import io.slychat.messenger.core.emptyByteArray
import org.spongycastle.crypto.digests.SHA512Digest
import org.spongycastle.crypto.generators.HKDFBytesGenerator
import org.spongycastle.crypto.params.HKDFParameters

fun deriveKey(masterKey: Key, info: HKDFInfo, outputKeySizeBits: Int): Key {
    require(outputKeySizeBits > 0) { "outputKeySize should be >= 0, got $outputKeySizeBits" }

    val hkdf = HKDFBytesGenerator(SHA512Digest())
    //the master key is directly from a PRNG, and the attacker has no knowledge of its structure so we don't require an extract stage
    val params = HKDFParameters.skipExtractParameters(masterKey.raw, info.raw)
    hkdf.init(params)

    val okm = ByteArray(outputKeySizeBits / 8)

    hkdf.generateBytes(okm, 0, okm.size)

    return Key(okm)
}

fun encryptBulkData(derivedKeySpec: DerivedKeySpec, data: ByteArray): ByteArray {
    return encryptBulkData(CipherList.defaultDataEncryptionCipher, derivedKeySpec, data)
}

fun encryptBulkData(cipher: Cipher, derivedKey: Key, data: ByteArray): ByteArray {
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

fun encryptBulkData(cipher: Cipher, derivedKeySpec: DerivedKeySpec, data: ByteArray): ByteArray {
    if (data.isEmpty())
        return emptyByteArray()

    val derivedKey = derivedKeySpec.derive(cipher.keySizeBits)

    return encryptBulkData(cipher, derivedKey, data)
}

//TODO add a no cipherId variant
fun decryptBulkData(derivedKeySpec: DerivedKeySpec, ciphertext: ByteArray): ByteArray {
    if (ciphertext.isEmpty())
        return emptyByteArray()

    if (ciphertext.isEmpty())
        throw IllegalArgumentException("Malformed ciphertext")

    val cipherId = CipherId(ciphertext[0].toShort())

    val cipher = CipherList.getCipher(cipherId)

    val derivedKey = derivedKeySpec.derive(cipher.keySizeBits)

    return cipher.decrypt(derivedKey, ciphertext.copyOfRange(1, ciphertext.size))
}
