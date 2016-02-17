package com.vfpowertech.keytap.core.persistence

import com.vfpowertech.keytap.core.crypto.JsonFileKeyVaultStorage
import com.vfpowertech.keytap.core.crypto.KeyVault
import nl.komponents.kovenant.Promise
import nl.komponents.kovenant.task
import java.io.File

/** Wrapper around a JsonFileKeyVaultStorage. */
class JsonKeyVaultPersistenceManager(private val path: File) : KeyVaultPersistenceManager {
    private val keyVaultStorage = JsonFileKeyVaultStorage(path)

    override fun retrieve(password: String): Promise<KeyVault?, Exception> = task {
        KeyVault.fromStorage(keyVaultStorage, password)
    }

    override fun store(keyVault: KeyVault): Promise<Unit, Exception> = task {
        keyVault.toStorage(keyVaultStorage)
    }

}