package io.slychat.messenger.core.persistence.json

import io.slychat.messenger.core.Quota
import io.slychat.messenger.core.crypto.DerivedKeySpec
import io.slychat.messenger.core.persistence.QuotaPersistenceManager
import java.io.File

class JSONQuotaPersistenceManager(
    private val path: File,
    private val derivedKeySpec: DerivedKeySpec
) : QuotaPersistenceManager {
    override fun store(quota: Quota) {
        writeEncryptedObjectToJsonFile(path, derivedKeySpec, quota)
    }

    override fun retrieve(): Quota? {
        return readEncryptedObjectFromJsonFile(path, derivedKeySpec, Quota::class.java)
    }
}