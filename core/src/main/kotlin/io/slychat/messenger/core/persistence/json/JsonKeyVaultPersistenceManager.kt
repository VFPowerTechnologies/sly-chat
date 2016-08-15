package io.slychat.messenger.core.persistence.json

import io.slychat.messenger.core.crypto.JsonFileKeyVaultStorage
import io.slychat.messenger.core.crypto.KeyVault
import io.slychat.messenger.core.persistence.KeyVaultPersistenceManager
import nl.komponents.kovenant.Promise
import nl.komponents.kovenant.task
import java.io.File

/** Wrapper around a JsonFileKeyVaultStorage. */
class JsonKeyVaultPersistenceManager(path: File) : KeyVaultPersistenceManager {
    private val keyVaultStorage = JsonFileKeyVaultStorage(path)

    override fun retrieve(password: String): Promise<KeyVault?, Exception> = task {
        KeyVault.fromStorage(keyVaultStorage, password)
    }

    override fun retrieveSync(password: String): KeyVault? =
        KeyVault.fromStorage(keyVaultStorage, password)

    override fun store(keyVault: KeyVault): Promise<Unit, Exception> = task {
        keyVault.toStorage(keyVaultStorage)
    }

}