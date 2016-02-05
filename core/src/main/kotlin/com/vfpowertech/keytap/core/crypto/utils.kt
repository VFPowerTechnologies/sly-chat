package com.vfpowertech.keytap.core.crypto

import com.vfpowertech.keytap.core.crypto.axolotl.GeneratedPreKeys
import com.vfpowertech.keytap.core.crypto.ciphers.AESGCMParams
import com.vfpowertech.keytap.core.crypto.ciphers.CipherParams
import com.vfpowertech.keytap.core.crypto.hashes.BCryptParams
import com.vfpowertech.keytap.core.crypto.hashes.HashParams
import com.vfpowertech.keytap.core.crypto.hashes.SHA256Params
import com.vfpowertech.keytap.core.require
import org.mindrot.jbcrypt.BCrypt
import org.whispersystems.libaxolotl.IdentityKeyPair
import org.whispersystems.libaxolotl.state.AxolotlStore
import org.whispersystems.libaxolotl.util.KeyHelper
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

fun ByteArray.hexify(): String =
    this.joinToString("") { "%02x".format(it) }

fun String.unhexify(): ByteArray {
    require((length % 2) == 0, "String length must be a multiple of 2")

    val bytes = ByteArray(length / 2)

    for (i in 0..length-1) {
        val v = this.subSequence(i*2, (i*2)+2).toString()
        bytes[i] = Integer.parseInt(v, 16).toByte()
    }

    return bytes
}

fun generateKeyPair(): IdentityKeyPair = KeyHelper.generateIdentityKeyPair()

data class PasswordHash(val hash: ByteArray, val params: HashParams)

/** Generates a hash for using the password as a symmetric encryption key. */
fun hashPasswordForLocal(password: String): PasswordHash {
    //use a proper hash since we need to do actually need to upload this remotely you idiot
    //bcrypt with a diff salt works, since scrypt and pbkdf2 utilitize sha256 which is still easy to implement using a gpu
    //although reusing the same algo for two diff
    val kdf = MessageDigest.getInstance("SHA256")
    kdf.update(password.toByteArray("utf8"))
    val hash = kdf.digest()
    return PasswordHash(hash, SHA256Params(ByteArray(0)))
}

/** Used to generate a password hash for a new password during registration. Uses the current default algorithm. */
fun hashPasswordForRemote(password: String): PasswordHash {
    //TODO calc this
    val cost = 20
    val salt = BCrypt.gensalt(cost)
    val params = BCryptParams(salt.toByteArray("ascii"), cost)
    val hash = BCrypt.hashpw(password, salt)

    return PasswordHash(hash.toByteArray("ascii"), params)
}

/**
 * Used to hash a password for authentication with the remote server using the provided params.
 *
 * @throws IllegalArgumentException If the type of CryptoParams is unknown
 */
fun hashPasswordWithParams(password: String, params: HashParams): ByteArray = when (params) {
    is BCryptParams -> BCrypt.hashpw(password, params.salt.toString()).toByteArray("ascii")
    else -> throw IllegalArgumentException("Unknown hash algorithm: ${params.algorithmName}")
}

data class EncryptedData(val data: ByteArray, val params: CipherParams)

/** Given a private key, uses a KDF to return a symmetric key of the given size */
fun privateKeyToSymKey(privateKeyBytes: ByteArray, params: HashParams): ByteArray {
    val kdf = MessageDigest.getInstance("SHA256")
    kdf.update(privateKeyBytes)
    return kdf.digest()
}

/** Return an IV of the given bit size. */
fun getIV(bits: Int): ByteArray {
    require(bits >= 8, "bits must be > 0")
    require((bits % 8) == 0, "bits must be a multiple of 8")

    val iv = ByteArray(bits/8)
    SecureRandom().nextBytes(iv)
    return iv
}

/** Encrypts data using the default settings. */
fun encryptData(key: SecretKey, plaintext: ByteArray): EncryptedData {
    val iv = getIV(256)
    val params = AESGCMParams(iv, 128)
    return encryptDataWithParams(key, plaintext, params)
}

/** Encrypt data with the given parameters. */
fun encryptDataWithParams(key: SecretKey, plaintext: ByteArray, params: CipherParams): EncryptedData = when (params) {
    is AESGCMParams -> {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        val spec = GCMParameterSpec(params.authTagLength, params.iv)
        cipher.init(Cipher.ENCRYPT_MODE, key, spec)
        val ciphertext = cipher.doFinal(plaintext)
        EncryptedData(ciphertext, params)
    }
    else -> throw IllegalArgumentException("Unknown cipher: ${params.algorithmName}")
}

/** Decrypt data with the given parameters. */
fun decryptData(key: SecretKey, ciphertext: ByteArray, params: CipherParams): ByteArray = when (params) {
    is AESGCMParams -> {
        val spec = GCMParameterSpec(params.authTagLength, params.iv)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, key, spec)
        cipher.doFinal(ciphertext)
    }
    else -> throw IllegalArgumentException("Unknown cipher: ${params.algorithmName}")
}

/** Generate a new batch of prekeys */
fun generatePrekeys(identityKeyPair: IdentityKeyPair, nextSignedPreKeyId: Int, nextPreKeyId: Int, count: Int): GeneratedPreKeys {
    val signedPrekey = KeyHelper.generateSignedPreKey(identityKeyPair, nextSignedPreKeyId)
    val oneTimePreKeys = KeyHelper.generatePreKeys(nextPreKeyId, count)
    val lastResortPreKey = KeyHelper.generateLastResortPreKey()

    return GeneratedPreKeys(signedPrekey, oneTimePreKeys, lastResortPreKey)
}

/** Add the prekeys into the given store */
fun addPreKeysToStore(axolotlStore: AxolotlStore, generatedPreKeys: GeneratedPreKeys) {
    axolotlStore.storeSignedPreKey(generatedPreKeys.signedPreKey.id, generatedPreKeys.signedPreKey)

    for (k in generatedPreKeys.oneTimePreKeys)
        axolotlStore.storePreKey(k.id, k)
    axolotlStore.storePreKey(generatedPreKeys.lastResortPreKey.id, generatedPreKeys.lastResortPreKey)
}
