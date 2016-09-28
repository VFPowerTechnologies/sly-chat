@file:JvmName("CryptoUtils")
package io.slychat.messenger.core.crypto

import io.slychat.messenger.core.base64encode
import io.slychat.messenger.core.crypto.ciphers.AESGCMParams
import io.slychat.messenger.core.crypto.ciphers.CipherParams
import io.slychat.messenger.core.crypto.hashes.BCryptParams
import io.slychat.messenger.core.crypto.hashes.HashParams
import io.slychat.messenger.core.crypto.hashes.SCryptParams
import io.slychat.messenger.core.crypto.hashes.SHA256Params
import io.slychat.messenger.core.crypto.signal.GeneratedPreKeys
import org.spongycastle.crypto.Digest
import org.spongycastle.crypto.digests.SHA256Digest
import org.spongycastle.crypto.engines.AESFastEngine
import org.spongycastle.crypto.generators.BCrypt
import org.spongycastle.crypto.generators.SCrypt
import org.spongycastle.crypto.modes.AEADBlockCipher
import org.spongycastle.crypto.modes.GCMBlockCipher
import org.spongycastle.crypto.params.AEADParameters
import org.spongycastle.crypto.params.KeyParameter
import org.whispersystems.libsignal.IdentityKey
import org.whispersystems.libsignal.IdentityKeyPair
import org.whispersystems.libsignal.state.PreKeyRecord
import org.whispersystems.libsignal.state.SignalProtocolStore
import org.whispersystems.libsignal.util.KeyHelper
import org.whispersystems.libsignal.util.Medium
import java.security.SecureRandom

fun ByteArray.hexify(): String =
    this.joinToString("") { "%02x".format(it) }

fun String.unhexify(): ByteArray {
    io.slychat.messenger.core.require((length % 2) == 0, "String length must be a multiple of 2")

    val bytes = ByteArray(length / 2)

    for (i in 0..bytes.size-1) {
        val v = this.subSequence(i*2, (i*2)+2).toString()
        bytes[i] = Integer.parseInt(v, 16).toByte()
    }

    return bytes
}

/** Default parameters for local data encryption. */
fun defaultDataEncryptionParams(): CipherParams =
    //12b is the default IV size
    AESGCMParams(getRandomBits(96), 128)

/** Default parameters for hashing a password into a key for decrypting the encrypted key pair. */
fun defaultKeyPasswordHashParams(): HashParams {
    val keyLength = 32
    val salt = getRandomBits(keyLength * 8)

    //TODO tune?
    //default recommendations from: https://www.tarsnap.com/scrypt/scrypt-slides.pdf
    val N = 16384
    val r = 8
    val p = 1

    return SCryptParams(
        salt,
        N,
        r,
        p,
        keyLength
    )
}

fun defaultRemotePasswordHashParams(): HashParams {
    //TODO calc this; 15 is too slow, 12 seems like a decent number time-wise
    val cost = 12
    val salt = getRandomBits(128)
    return BCryptParams(salt, cost)
}

fun generateKeyPair(): IdentityKeyPair = KeyHelper.generateIdentityKeyPair()

class HashData(val hash: ByteArray, val params: HashParams)

/**
 * Generates a hash for using the password as a symmetric encryption key. The generate key should have the same key
 * length as the corresponding cipher.
 */
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
        //this design is from passlib, to get around the 72 password length limitation (incase)
        //see https://pythonhosted.org/passlib/lib/passlib.hash.bcrypt_sha256.html
        val digester = SHA256Digest()
        val hash = digester.processInput(password.toByteArray(Charsets.UTF_8))

        //44b string
        val encoded = base64encode(hash).toByteArray(Charsets.US_ASCII)

        //password must be nul-terminated to prevent collisions in repeated passwords (eg: test and testtest)
        val input = ByteArray(encoded.size+1)
        System.arraycopy(encoded, 0, input, 0, encoded.size)

        BCrypt.generate(input, params.salt, params.cost)
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
        val digester = SHA256Digest()
        val salt = params.salt
        val toHash = if (salt.size > 0) {
            val buffer = ByteArray(data.size+salt.size)
            System.arraycopy(data, 0, buffer, 0, data.size)
            System.arraycopy(salt, 0, buffer, data.size, salt.size)
            buffer
        }
        else
            data

        digester.processInput(toHash)
    }

    is SCryptParams ->
        SCrypt.generate(data, params.salt, params.N, params.r, params.p, params.keyLength)

    else -> throw IllegalArgumentException("Unknown data hash algorithm: ${params.algorithmName}")
}

class EncryptionSpec(val key: ByteArray, val params: CipherParams)

class EncryptedData(val data: ByteArray, val params: CipherParams)

/** Return a randomly generated ByteArray of the given bit size. */
fun getRandomBits(bits: Int): ByteArray {
    io.slychat.messenger.core.require(bits >= 8, "bits must be > 8")
    io.slychat.messenger.core.require((bits % 8) == 0, "bits must be a multiple of 8")

    val iv = ByteArray(bits/8)
    SecureRandom().nextBytes(iv)
    return iv
}

/** Encrypts data using the default settings. */
fun encryptData(key: ByteArray, plaintext: ByteArray): EncryptedData {
    val params = defaultDataEncryptionParams()
    return encryptDataWithParams(EncryptionSpec(key, params), plaintext)
}

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

private fun Digest.processInput(input: ByteArray): ByteArray {
    val output = ByteArray(digestSize)
    update(input, 0, input.size)
    doFinal(output, 0)
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

val LAST_RESORT_PREKEY_ID = Medium.MAX_VALUE

/** Generates a last resort prekey. Should only be generated once. This key will always have id set to Medium.MAX_VALUE. */
fun generateLastResortPreKey(): PreKeyRecord =
    KeyHelper.generateLastResortPreKey()

/** Generate a new batch of prekeys */
fun generatePrekeys(identityKeyPair: IdentityKeyPair, nextSignedPreKeyId: Int, nextPreKeyId: Int, count: Int): GeneratedPreKeys {
    io.slychat.messenger.core.require(nextSignedPreKeyId > 0, "nextSignedPreKeyId must be > 0")
    io.slychat.messenger.core.require(nextPreKeyId > 0, "nextPreKeyId must be > 0")
    io.slychat.messenger.core.require(count > 0, "count must be > 0")

    val signedPrekey = KeyHelper.generateSignedPreKey(identityKeyPair, nextSignedPreKeyId)
    val oneTimePreKeys = KeyHelper.generatePreKeys(nextPreKeyId, count)

    return GeneratedPreKeys(signedPrekey, oneTimePreKeys)
}

/** Add the prekeys into the given store */
fun addPreKeysToStore(signalStore: SignalProtocolStore, generatedPreKeys: GeneratedPreKeys) {
    signalStore.storeSignedPreKey(generatedPreKeys.signedPreKey.id, generatedPreKeys.signedPreKey)

    for (k in generatedPreKeys.oneTimePreKeys)
        signalStore.storePreKey(k.id, k)
}

/** Should only be done once. */
fun addLastResortPreKeyToStore(signalStore: SignalProtocolStore, lastResortPreKey: PreKeyRecord) {
    signalStore.storePreKey(lastResortPreKey.id, lastResortPreKey)
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

fun identityKeyFingerprint(identityKey: IdentityKey): String =
    identityKey.publicKey.serialize().hexify()

/** Returns a random UUID as a string, without dashes. */
fun randomUUID(): String {
    val bytes = ByteArray(16)
    SecureRandom().nextBytes(bytes)
    return bytes.hexify()
}

private val uuidRegex = "[0-9a-f]{32}".toRegex()
fun isValidUUIDFormat(s: String): Boolean {
    return uuidRegex.matches(s)
}

fun randomRegistrationId(): Int = KeyHelper.generateRegistrationId(false)

fun randomMessageId(): String = randomUUID()
