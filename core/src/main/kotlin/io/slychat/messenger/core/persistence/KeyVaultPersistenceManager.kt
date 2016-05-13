package io.slychat.messenger.core.persistence

import io.slychat.messenger.core.crypto.KeyVault
import nl.komponents.kovenant.Promise

interface KeyVaultPersistenceManager {
    fun retrieve(password: String): Promise<KeyVault, Exception>
    fun store(keyVault: KeyVault): Promise<Unit, Exception>
}