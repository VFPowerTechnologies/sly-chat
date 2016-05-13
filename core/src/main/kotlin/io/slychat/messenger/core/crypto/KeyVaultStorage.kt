package io.slychat.messenger.core.crypto

/**
 * Interface for reading/writing a KeyVault.
 *
 * Stored data:
 *
 * 1) encrypted public+private key data (simplier for decserialization)
 *
 * Stored params:
 *
 * 1) Hash for textual password -> key pair encryption/decryption key
 * 2) Cipher for key pair encryption/decryption
 * 3) Hash for private key -> local data encryption/decryption key
 * 4) Cipher for local data encryption/decryption key
 *
 */
interface KeyVaultStorage {
    fun read(): SerializedKeyVault
    fun write(serializedKeyVault: SerializedKeyVault)
}