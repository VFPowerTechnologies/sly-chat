package io.slychat.messenger.core.crypto

import io.slychat.messenger.core.crypto.ciphers.CipherList
import io.slychat.messenger.core.crypto.ciphers.Key
import io.slychat.messenger.core.crypto.ciphers.decryptBulkData
import io.slychat.messenger.core.crypto.ciphers.encryptBulkData
import io.slychat.messenger.core.crypto.hashes.HashParams
import io.slychat.messenger.core.crypto.hashes.HashType
import io.slychat.messenger.core.crypto.hashes.hashPasswordWithParams
import org.spongycastle.crypto.InvalidCipherTextException
import org.whispersystems.libsignal.IdentityKeyPair

/**
 * Interface for accessing a user's identity keypair and hashes.
 *
 * @property identityKeyPair
 * @property masterKey Used to derive other keys on-demand via HKDF.
 * @property localPasswordHashParams How to hash the password to use as a key for encrypting/decrypting the key vault secrets.
 *
 */
class KeyVault(
    //encrypted using password-derived key
    val identityKeyPair: IdentityKeyPair,
    //encrypted by password-derived key
    //all other keys are derived from this one
    val masterKey: Key,
    //used when creating hashes of things to upload remotely to allow the hashes to match across devices, but not be identifiable to others
    val anonymizingData: ByteArray,
    //PBKDF params for key to decrypt masterKey and identityKeyPair
    val localPasswordHashParams: HashParams,
    //kept so we can serialize
    private val localPasswordHash: Key
) {
    companion object {
        fun fromStorage(keyVaultStorage: KeyVaultStorage, password: String): KeyVault? =
            keyVaultStorage.read()?.let { deserialize(it, password) }

        fun deserialize(serialized: SerializedKeyVault, password: String): KeyVault {
            try {
                val localPasswordHash = Key(hashPasswordWithParams(password, serialized.localPasswordHashParams, HashType.LOCAL))

                val masterKey = Key(decryptBulkData(
                    DerivedKeySpec(localPasswordHash, HKDFInfoList.keyVaultMasterKey()),
                    serialized.encryptedMasterKey
                ))

                val keyData = decryptBulkData(
                    DerivedKeySpec(localPasswordHash, HKDFInfoList.keyVaultKeyPair()),
                    serialized.encryptedKeyPair
                )

                val anonymizingData = decryptBulkData(
                    DerivedKeySpec(localPasswordHash, HKDFInfoList.keyVaultAnonymizingData()),
                    serialized.encryptedAnonymizingData
                )

                val identityKeyPair = IdentityKeyPair(keyData)

                return KeyVault(
                    identityKeyPair,
                    masterKey,
                    anonymizingData,
                    serialized.localPasswordHashParams,
                    localPasswordHash
                )
            }
            catch (e: InvalidCipherTextException) {
                throw KeyVaultDecryptionFailedException()
            }
        }
    }

    /** Returns the public key encoded as a hex string. */
    val fingerprint: String
        get() {
            //this includes the prepended type byte
            return identityKeyFingerprint(identityKeyPair.publicKey)
        }

    fun serialize(): SerializedKeyVault {
        val cipher = CipherList.defaultDataEncryptionCipher
        val key = localPasswordHash

        val encryptedMasterKey = encryptBulkData(
            cipher,
            DerivedKeySpec(key, HKDFInfoList.keyVaultMasterKey()),
            masterKey.raw
        )

        val encryptedKeyPair = encryptBulkData(
            cipher,
            DerivedKeySpec(key, HKDFInfoList.keyVaultKeyPair()),
            identityKeyPair.serialize()
        )

        val encryptedAnonymizingData = encryptBulkData(
            cipher,
            DerivedKeySpec(key, HKDFInfoList.keyVaultAnonymizingData()),
            anonymizingData
        )

        return SerializedKeyVault(
            encryptedKeyPair,
            encryptedMasterKey,
            encryptedAnonymizingData,
            localPasswordHashParams
        )
    }

    fun toStorage(keyVaultStorage: KeyVaultStorage) {
        keyVaultStorage.write(serialize())
    }
}
