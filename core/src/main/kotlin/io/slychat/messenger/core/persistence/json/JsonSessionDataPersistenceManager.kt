package io.slychat.messenger.core.persistence.json

import io.slychat.messenger.core.crypto.DerivedKeySpec
import io.slychat.messenger.core.persistence.SessionData
import io.slychat.messenger.core.persistence.SessionDataPersistenceManager
import nl.komponents.kovenant.Promise
import nl.komponents.kovenant.task
import java.io.File

class JsonSessionDataPersistenceManager(
    val path: File,
    private val derivedKeySpec: DerivedKeySpec
) : SessionDataPersistenceManager {
    override fun store(sessionData: SessionData): Promise<Unit, Exception> = task {
        writeEncryptedObjectToJsonFile(path, derivedKeySpec, sessionData)
    }

    override fun retrieve(): Promise<SessionData?, Exception> = task {
        retrieveSync()
    }

    override fun retrieveSync(): SessionData? {
        return readEncryptedObjectFromJsonFile(path, derivedKeySpec, SessionData::class.java)
    }

    override fun delete(): Promise<Boolean, Exception> = task {
        path.delete()
    }
}