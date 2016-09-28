package io.slychat.messenger.core.crypto

import io.slychat.messenger.core.crypto.ciphers.CipherParams
import io.slychat.messenger.core.crypto.hashes.HashParams
import io.slychat.messenger.core.hexify
import io.slychat.messenger.core.unhexify
import org.spongycastle.crypto.InvalidCipherTextException
import org.whispersystems.libsignal.IdentityKeyPair

/**
 * Interface for accessing a user's identity keypair and hashes.
 *
 * @property identityKeyPair
 * @property remotePasswordHash Password hash for authenticating to the chat service. Is set after receiving a challenge from a server.
 * @property remotePasswordHashParams Hash Params for authenticating to the chat server. Used to detect an algorithm change and re-prompt the user for a password
 * @property keyPasswordHash Hash for encrypting/decrypting the identity key pair.
 * @property localDataEncryptionKey Key used for local data encryption. Derived from the user's private key via a KDF.
 * @property keyPasswordHashParams How to hash the password to use as a key for encrypting/decrypting the encrypted key pair.
 * @property keyPairCipherParams How the key itself is encrypted.
 * @property privateKeyHashParams How to derive a symmetric encryption key from a private key for local encryption.
 * @property localDataEncryptionParams How local data is encrypted/decrypted.
 *
 */
class KeyVault(
    val identityKeyPair: IdentityKeyPair,

    val remotePasswordHash: ByteArray,
    val remotePasswordHashParams: HashParams,
    val keyPasswordHash: ByteArray,

    val keyPasswordHashParams: HashParams,
    val keyPairCipherParams: CipherParams,
    val privateKeyHashParams: HashParams,

    val localDataEncryptionKey: ByteArray,
    val localDataEncryptionParams: CipherParams
) {
    private fun getEncryptedPrivateKey(): ByteArray {
        val key = keyPasswordHash
        return encryptDataWithParams(EncryptionSpec(key, keyPairCipherParams), identityKeyPair.serialize()).data
    }

    private fun getEncryptedRemotePasswordHash(): ByteArray {
        val key = localDataEncryptionKey
        return encryptDataWithParams(EncryptionSpec(key, localDataEncryptionParams), remotePasswordHash).data
    }

    /** Returns the public key encoded as a hex string. */
    val fingerprint: String
        get() {
            //this includes the prepended type byte
            return identityKeyFingerprint(identityKeyPair.publicKey)
        }

    fun serialize(): SerializedKeyVault {
        val encryptedKeyPair = getEncryptedPrivateKey()

        return SerializedKeyVault(
            encryptedKeyPair.hexify(),
            keyPasswordHashParams.serialize(),
            keyPairCipherParams.serialize(),
            privateKeyHashParams.serialize(),
            localDataEncryptionParams.serialize(),
            getEncryptedRemotePasswordHash().hexify(),
            remotePasswordHashParams.serialize())
    }

    fun toStorage(keyVaultStorage: KeyVaultStorage) {
        keyVaultStorage.write(serialize())
    }

    companion object {
        fun fromStorage(keyVaultStorage: KeyVaultStorage, password: String): KeyVault? =
            keyVaultStorage.read()?.let { deserialize(it, password) }

        fun deserialize(serialized: SerializedKeyVault, password: String): KeyVault {
            val encryptedKeyPairData = serialized.encryptedKeyPair

            val keyPairCipherParams = CipherDeserializers.deserialize(
                serialized.keyPairCipherParams)

            val keyPasswordHashParams = HashDeserializers.deserialize(
                serialized.keyPasswordHashParams)

            val keyPasswordHash = hashPasswordWithParams(password, keyPasswordHashParams)
            val keyKey = keyPasswordHash

            val decryptedKeyData = try {
                decryptData(EncryptionSpec(keyKey, keyPairCipherParams), encryptedKeyPairData.unhexify())
            }
            catch (e: InvalidCipherTextException) {
                throw KeyVaultDecryptionFailedException()
            }

            val identityKeyPair = IdentityKeyPair(decryptedKeyData)

            val keyHashParams = HashDeserializers.deserialize(
                serialized.privateKeyHashParams)

            val localEncryptionKey = hashDataWithParams(identityKeyPair.privateKey.serialize(), keyHashParams)

            val localDataEncryptionParams = CipherDeserializers.deserialize(
                serialized.localDataEncryptionParams)

            val dataKey = localEncryptionKey

            val remotePasswordHash = decryptData(EncryptionSpec(dataKey, localDataEncryptionParams), serialized.encryptedRemotePasswordHash.unhexify())

            val remotePasswordHashParams = HashDeserializers.deserialize(serialized.remotePasswordHashParams)

            return KeyVault(
                identityKeyPair,

                remotePasswordHash,
                remotePasswordHashParams,
                keyPasswordHash,

                keyPasswordHashParams,
                keyPairCipherParams,

                keyHashParams,
                localEncryptionKey,
                localDataEncryptionParams
            )
        }
    }
}
