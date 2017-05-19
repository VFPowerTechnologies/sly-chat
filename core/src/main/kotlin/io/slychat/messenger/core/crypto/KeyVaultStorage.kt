package io.slychat.messenger.core.crypto

/** Interface for reading/writing a [SerializedKeyVault]. */
interface KeyVaultStorage {
    fun read(): SerializedKeyVault?

    fun write(serializedKeyVault: SerializedKeyVault)
}