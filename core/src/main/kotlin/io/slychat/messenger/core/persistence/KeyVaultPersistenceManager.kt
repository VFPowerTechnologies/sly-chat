package io.slychat.messenger.core.persistence

import io.slychat.messenger.core.crypto.KeyVault
import nl.komponents.kovenant.Promise

interface KeyVaultPersistenceManager {
    /** May throw KeyVaultDecryptionFailedException. */
    fun retrieve(password: String): Promise<KeyVault?, Exception>
    fun retrieveSync(password: String): KeyVault?
    fun store(keyVault: KeyVault): Promise<Unit, Exception>
}