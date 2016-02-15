package com.vfpowertech.keytap.core.crypto

import com.vfpowertech.keytap.core.crypto.axolotl.GeneratedPreKeys
import com.vfpowertech.keytap.core.crypto.ciphers.AESGCMParams
import com.vfpowertech.keytap.core.crypto.ciphers.CipherParams
import com.vfpowertech.keytap.core.crypto.hashes.BCryptParams
import com.vfpowertech.keytap.core.crypto.hashes.HashParams
import com.vfpowertech.keytap.core.crypto.hashes.SHA256Params
import com.vfpowertech.keytap.core.require
import org.mindrot.jbcrypt.BCrypt
import org.spongycastle.crypto.engines.AESFastEngine
import org.spongycastle.crypto.modes.AEADBlockCipher
import org.spongycastle.crypto.modes.GCMBlockCipher
import org.spongycastle.crypto.params.AEADParameters
import org.spongycastle.crypto.params.KeyParameter
import org.whispersystems.libaxolotl.IdentityKeyPair
import org.whispersystems.libaxolotl.state.AxolotlStore
import org.whispersystems.libaxolotl.util.KeyHelper
import org.whispersystems.libaxolotl.util.Medium
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.SecretKey

//KeyHelper caps keys at Medium.VALUE-1 (unsigned 24bit)
//the last resort prekey always has id=Medium.VALUE
//generatePreKeys handles this via modulo arithmetic, but for signed keys we need to do it ourselves
val MIN_PRE_KEY_ID = 1
val MAX_PRE_KEY_ID = Medium.MAX_VALUE-1

/** Returns a random prekey id. */
fun randomPreKeyId(): Int {
    val rand = SecureRandom()
    return MIN_PRE_KEY_ID + rand.nextInt((MAX_PRE_KEY_ID - MIN_PRE_KEY_ID) + 1)
}

/** Returns the next available id. On overflow, loops back to the min value. */
fun nextPreKeyId(current: Int): Int {
    if (current == MAX_PRE_KEY_ID)
        return MIN_PRE_KEY_ID
    else
        return current + 1
}

fun ByteArray.hexify(): String =
    this.joinToString("") { "%02x".format(it) }

fun String.unhexify(): ByteArray {
    require((length % 2) == 0, "String length must be a multiple of 2")

    val bytes = ByteArray(length / 2)

    for (i in 0..bytes.size-1) {
        val v = this.subSequence(i*2, (i*2)+2).toString()
        bytes[i] = Integer.parseInt(v, 16).toByte()
    }

    return bytes
}

/** Default parameters for local data encryption. */
fun defaultDataEncryptionParams(): CipherParams =
    AESGCMParams(getIV(256), 128)

//use a proper hash since we actually do need to upload this remotely you idiot
//bcrypt with a diff salt works, since scrypt and pbkdf2 utilitize sha256 which is still easy to implement using a gpu
//although reusing the same algo for two diff things leads to higher chance of a salt+password clash
/** Default parameters for hashing a password into a key for decrypting the encrypted key pair. */
fun defaultKeyPasswordHashParams(): HashParams {
    val salt = ByteArray(256/8)
    SecureRandom().nextBytes(salt)
    return SHA256Params(salt)
}

fun defaultRemotePasswordHashParams(): HashParams {
    //TODO calc this; 15 is too slow, 12 seems like a decent number time-wise
    val cost = 12
    val salt = BCrypt.gensalt(cost)
    return BCryptParams(salt, cost)
}

fun generateKeyPair(): IdentityKeyPair = KeyHelper.generateIdentityKeyPair()

data class HashData(val hash: ByteArray, val params: HashParams)

/** Generates a hash for using the password as a symmetric encryption key. */
fun hashPasswordForLocalWithDefaults(password: String): HashData {
    val params = defaultKeyPasswordHashParams()
    return HashData(hashDataWithParams(password.toByteArray(Charsets.UTF_8), params), params)
}

/** Used to generate a password hash for a new password during registration. Uses the current default algorithm. */
fun hashPasswordForRemoteWithDefaults(password: String): HashData {
    val params = defaultRemotePasswordHashParams()
    return HashData(hashPasswordWithParams(password, params), params)
}

/**
 * Used to hash a password for authentication with the remote server using the provided params.
 *
 * @throws IllegalArgumentException If the type of CryptoParams is unknown
 */
fun hashPasswordWithParams(password: String, params: HashParams): ByteArray = when (params) {
    is BCryptParams -> {
        //just grab the hash, even if it's base64
        val s = BCrypt.hashpw(password, params.salt)
        val hash = s.substring(s.length-31, s.length)
        hash.toByteArray(Charsets.US_ASCII)
    }
    else -> hashDataWithParams(password.toByteArray(Charsets.UTF_8), params)
}

/** Converts a private key for use as a symmetric key. Currently returns a 256bit key. */
fun privateKeyToSymmetricKey(privateKeyBytes: ByteArray): HashData {
    val params = SHA256Params(ByteArray(0))
    return HashData(hashDataWithParams(privateKeyBytes, params), params)
}

fun hashDataWithParams(data: ByteArray, params: HashParams): ByteArray = when (params) {
    is SHA256Params -> {
        val kdf = MessageDigest.getInstance("SHA-256")
        val salt = params.salt
        val toHash = if (salt.size > 0) {
            val buffer = ByteArray(data.size+salt.size)
            System.arraycopy(data, 0, buffer, 0, data.size)
            System.arraycopy(salt, 0, buffer, data.size, salt.size)
            buffer
        }
        else
            data

        kdf.update(toHash)
        kdf.digest()
    }

    else -> throw IllegalArgumentException("Unknown data hash algorithm: ${params.algorithmName}")
}

data class EncryptedData(val data: ByteArray, val params: CipherParams)

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
    val params = defaultDataEncryptionParams()
    return encryptDataWithParams(key, plaintext, params)
}

private fun newGCMCipher(forEncryption: Boolean, params: AESGCMParams, key: SecretKey): AEADBlockCipher {
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

private fun AESGCMParams.toAEADParameters(key: SecretKey): AEADParameters {
    val keyParam = KeyParameter(key.encoded)
    return AEADParameters(keyParam, authTagLength, iv)
}

/** Encrypt data with the given parameters. */
fun encryptDataWithParams(key: SecretKey, plaintext: ByteArray, params: CipherParams): EncryptedData = when (params) {
    is AESGCMParams -> {
        val cipher = newGCMCipher(true, params, key)
        val ciphertext = cipher.processInput(plaintext)

        EncryptedData(ciphertext, params)
    }
    else -> throw IllegalArgumentException("Unknown cipher: ${params.algorithmName}")
}

/** Decrypt data with the given parameters. */
fun decryptData(key: SecretKey, ciphertext: ByteArray, params: CipherParams): ByteArray = when (params) {
    is AESGCMParams -> {
        val cipher = newGCMCipher(false, params, key)
        cipher.processInput(ciphertext)
    }
    else -> throw IllegalArgumentException("Unknown cipher: ${params.algorithmName}")
}

/** Generate a new batch of prekeys */
fun generatePrekeys(identityKeyPair: IdentityKeyPair, nextSignedPreKeyId: Int, nextPreKeyId: Int, count: Int): GeneratedPreKeys {
    require(nextSignedPreKeyId > 0, "nextSignedPreKeyId must be > 0")
    require(nextPreKeyId > 0, "nextPreKeyId must be > 0")
    require(count > 0, "count must be > 0")

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

/** Generates a new key vault for a new user. For use during registration. */
fun generateNewKeyVault(password: String): KeyVault {
    val identityKeyPair = generateKeyPair()
    val keyPasswordHashInfo = hashPasswordForLocalWithDefaults(password)
    //on signup, the client can choose the algo
    //this can be updated later
    val remotePasswordHashInfo = hashPasswordForRemoteWithDefaults(password)

    val localEncryptionKeyInfo = privateKeyToSymmetricKey(identityKeyPair.privateKey.serialize())


    return KeyVault(
        identityKeyPair,
        remotePasswordHashInfo.hash,
        remotePasswordHashInfo.params,
        keyPasswordHashInfo.hash,
        keyPasswordHashInfo.params,
        defaultDataEncryptionParams(),
        localEncryptionKeyInfo.params,
        localEncryptionKeyInfo.hash,
        defaultDataEncryptionParams()
    )
}
