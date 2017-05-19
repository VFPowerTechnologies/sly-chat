package io.slychat.messenger.core.crypto

import com.fasterxml.jackson.databind.ObjectMapper
import io.slychat.messenger.core.crypto.ciphers.*
import io.slychat.messenger.core.crypto.hashes.HashParams
import org.whispersystems.libsignal.IdentityKeyPair

/**
 * Interface for accessing a user's identity keypair and other secrets.
 *
 * @property identityKeyPair Signal identity key pair.
 * @property masterKey Used to derive other keys on-demand via HKDF. All non-local data is encrypted using subkeys derived from the master key.
 * @property anonymizingData Used when creating hashes of things to upload remotely to allow the hashes to match across devices for the same account, but not be identifiable to others.
 * @property localPasswordHashParams How to derive a key from the password to use for encrypting/decrypting the SerializedKeyVault data.
 * @property localPasswordHash Kept around so we can easily serialize the KeyVault on-demand.
 */
class KeyVault(
    val identityKeyPair: IdentityKeyPair,
    private val masterKey: Key,
    val anonymizingData: ByteArray,
    //PBKDF params for key to decrypt secrets during deserialization
    private val localPasswordHashParams: HashParams,
    private val localPasswordHash: Key
) {
    companion object {
        /** Read a KeyVault from storage. */
        fun fromStorage(keyVaultStorage: KeyVaultStorage, password: String): KeyVault? =
            keyVaultStorage.read()?.deserialize(password)
    }

    /** Returns the public key encoded as a hex string. This includes the prepended type byte. */
    val fingerprint: String
        get() = identityKeyFingerprint(identityKeyPair.publicKey)

    /** Serialize (and encrypt) KeyVault data. Decryption and deserialization is handled by [SerializedKeyVault.deserialize]. */
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
