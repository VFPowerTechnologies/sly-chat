package com.vfpowertech.keytap.core.persistence

import com.vfpowertech.keytap.core.crypto.KeyVault
import nl.komponents.kovenant.Promise

interface KeyVaultPersistenceManager {
    fun retrieve(password: String): Promise<KeyVault?, Exception>
    fun store(keyVault: KeyVault): Promise<Unit, Exception>
}