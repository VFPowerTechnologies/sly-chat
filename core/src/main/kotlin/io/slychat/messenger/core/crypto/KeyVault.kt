package io.slychat.messenger.core.crypto

import com.fasterxml.jackson.databind.ObjectMapper
import io.slychat.messenger.core.crypto.ciphers.*
import io.slychat.messenger.core.crypto.hashes.HashParams
import org.whispersystems.libsignal.IdentityKeyPair

/**
 * Interface for accessing a user's identity keypair and other secrets.
 *
 * @property identityKeyPair
 * @property masterKey Used to derive other keys on-demand via HKDF.
 * @property localPasswordHashParams How to derive a key from the password to use for encrypting/decrypting the key vault secrets.
 *
 */
class KeyVault(
    val identityKeyPair: IdentityKeyPair,
    //all other keys are derived from this one
    private val masterKey: Key,
    //used when creating hashes of things to upload remotely to allow the hashes to match across devices, but not be identifiable to others
    val anonymizingData: ByteArray,
    //PBKDF params for key to decrypt secrets during deserialization
    private val localPasswordHashParams: HashParams,
    //kept so we can serialize
    private val localPasswordHash: Key
) {
    companion object {
        fun fromStorage(keyVaultStorage: KeyVaultStorage, password: String): KeyVault? =
            keyVaultStorage.read()?.let { it.deserialize(password) }
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

        val derivedKey = deriveKey(key, HKDFInfoList.keyVault(), cipher.keySizeBits)

        val secrets = SerializedKeyVaultSecrets(
            identityKeyPair.serialize(),
            masterKey,
            anonymizingData
        )

        val objectMapper = ObjectMapper()

        val encryptedSecrets = encryptBulkData(
            cipher,
            derivedKey,
            objectMapper.writeValueAsBytes(secrets)
        )

        return SerializedKeyVault(
            1,
            localPasswordHashParams,
            encryptedSecrets
        )
    }

    fun toStorage(keyVaultStorage: KeyVaultStorage) {
        keyVaultStorage.write(serialize())
    }

    private fun infoForType(type: DerivedKeyType): HKDFInfo = when (type) {
        DerivedKeyType.ACCOUNT_LOCAL_INFO -> HKDFInfoList.accountLocalInfo()
        DerivedKeyType.REMOTE_ADDRESS_BOOK_ENTRIES -> HKDFInfoList.remoteAddressBookEntries()
        //TODO maybe add the fileId into this
        DerivedKeyType.USER_METADATA -> HKDFInfoList.userMetadata()
    }

    fun getDerivedKeySpec(type: DerivedKeyType): DerivedKeySpec {
        return DerivedKeySpec(masterKey, infoForType(type))
    }

    fun deriveKeyFor(type: DerivedKeyType, cipher: Cipher): Key {
        return deriveKey(masterKey, infoForType(type), cipher.keySizeBits)
    }
}
