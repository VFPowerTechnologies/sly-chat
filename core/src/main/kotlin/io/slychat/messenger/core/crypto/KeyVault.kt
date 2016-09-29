package io.slychat.messenger.core.crypto

import io.slychat.messenger.core.crypto.ciphers.CipherList
import io.slychat.messenger.core.crypto.ciphers.decryptBulkData
import io.slychat.messenger.core.crypto.ciphers.encryptBulkData
import io.slychat.messenger.core.crypto.hashes.HashParams
import io.slychat.messenger.core.crypto.hashes.hashPasswordWithParams
import org.spongycastle.crypto.InvalidCipherTextException
import org.whispersystems.libsignal.IdentityKeyPair

/**
 * Interface for accessing a user's identity keypair and hashes.
 *
 * @property identityKeyPair
 * @property masterKey Used to derive other keys on-demand via HKDF.
 * @property keyPasswordHashParams How to hash the password to use as a key for encrypting/decrypting the master key and encrypted key pair.
 *
 */
class KeyVault(
    //encrypted using password-derived key
    val identityKeyPair: IdentityKeyPair,
    //encrypted by password-derived key
    //all other keys are derived from this one
    val masterKey: ByteArray,
    //used when creating hashes of things to upload remotely to allow the hashes to match across devices, but not be identifiable to others
    val anonymizingData: ByteArray,
    //PBKDF params for key to decrypt masterKey and identityKeyPair
    val localPasswordHashParams: HashParams,

    //kept so we can serialize
    private val localPasswordHash: ByteArray
) {

    /** Returns the public key encoded as a hex string. */
    val fingerprint: String
        get() {
            //this includes the prepended type byte
            return identityKeyFingerprint(identityKeyPair.publicKey)
        }

    fun serialize(): SerializedKeyVault {
        val cipher = CipherList.defaultDataEncryptionCipher
        val key = localPasswordHash

        val encryptedMasterKey = encryptBulkData(cipher, key, masterKey, HKDFInfo.keyVaultMasterKey())
        val encryptedKeyPair = encryptBulkData(cipher, key, identityKeyPair.serialize(), HKDFInfo.keyVaultKeyPair())
        val encryptedAnonymizingData = encryptBulkData(cipher, key, anonymizingData, HKDFInfo.keyVaultAnonymizingData())

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

    companion object {
        fun fromStorage(keyVaultStorage: KeyVaultStorage, password: String): KeyVault? =
            keyVaultStorage.read()?.let { deserialize(it, password) }

        fun deserialize(serialized: SerializedKeyVault, password: String): KeyVault {
            try {
                val localPasswordHash = hashPasswordWithParams(password, serialized.localPasswordHashParams)

                val masterKey = decryptBulkData(localPasswordHash, serialized.encryptedMasterKey, HKDFInfo.keyVaultMasterKey())
                val keyData = decryptBulkData(localPasswordHash, serialized.encryptedKeyPair, HKDFInfo.keyVaultKeyPair())
                val anonymizingData = decryptBulkData(localPasswordHash, serialized.encryptedAnonymizingData, HKDFInfo.keyVaultAnonymizingData())

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
}
