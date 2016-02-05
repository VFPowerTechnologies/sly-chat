package com.vfpowertech.keytap.core.crypto

import com.vfpowertech.keytap.core.crypto.ciphers.CipherParams
import com.vfpowertech.keytap.core.crypto.hashes.HashParams
import org.whispersystems.libaxolotl.IdentityKeyPair
import java.io.File
import javax.crypto.spec.SecretKeySpec

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

    var remotePasswordHash: ByteArray?,
    var remotePasswordHashParams: HashParams?,
    val keyPasswordHash: ByteArray,

    val keyPasswordHashParams: HashParams,
    val keyPairCipherParams: CipherParams,
    val privateKeyHashParams: HashParams,

    val localDataEncryptionKey: ByteArray,
    val localDataEncryptionParams: CipherParams
) {
    fun serialize(): SerializedKeyVault {
        val key = SecretKeySpec(keyPasswordHash, keyPairCipherParams.keyType)
        val encryptedKeyPair = encryptDataWithParams(key, identityKeyPair.serialize(), keyPairCipherParams)

        return SerializedKeyVault(
            encryptedKeyPair.data.hexify(),
            keyPasswordHashParams.serialize(),
            keyPairCipherParams.serialize(),
            privateKeyHashParams.serialize(),
            localDataEncryptionParams.serialize())
    }

    fun toStorage(keyVaultStorage: KeyVaultStorage) {
        keyVaultStorage.write(serialize())
    }

    companion object {
        fun fromStorage(keyVaultStorage: KeyVaultStorage, password: String): KeyVault {
            val serialized = keyVaultStorage.read()

            val encryptedKeyPairData = serialized.encryptedKeyPair

            val keyPairCipherParams = CipherDeserializers.deserialize(
                serialized.keyPairCipherParams)

            val keyPasswordHashParams = HashDeserializers.deserialize(
                serialized.keyPasswordHashParams)

            val keyPasswordHash = hashPasswordWithParams(password, keyPasswordHashParams)
            System.out.flush()
            val key = SecretKeySpec(keyPasswordHash, keyPairCipherParams.keyType)

            val decryptedKeyData = decryptData(key, encryptedKeyPairData.unhexify(), keyPairCipherParams)
            val identityKeyPair = IdentityKeyPair(decryptedKeyData)

            val keyHashParams = HashDeserializers.deserialize(
                serialized.privateKeyHashParams)

            val localEncryptionKey = hashDataWithParams(identityKeyPair.privateKey.serialize(), keyHashParams)

            val localDataEncryptionParams = CipherDeserializers.deserialize(
                serialized.localDataEncryptionParams)

            return KeyVault(
                identityKeyPair,

                null,
                null,
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
