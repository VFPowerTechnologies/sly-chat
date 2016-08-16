package io.slychat.messenger.core.persistence.json

import com.fasterxml.jackson.databind.ObjectMapper
import io.slychat.messenger.core.crypto.ciphers.CipherParams
import io.slychat.messenger.core.persistence.SerializedSessionData
import io.slychat.messenger.core.persistence.SessionData
import io.slychat.messenger.core.persistence.SessionDataPersistenceManager
import nl.komponents.kovenant.Promise
import nl.komponents.kovenant.task
import java.io.File

class JsonSessionDataPersistenceManager(
    val path: File,
    private val localDataEncryptionKey: ByteArray,
    private val localDataEncryptionParams: CipherParams
) : SessionDataPersistenceManager {
    val objectMapper = ObjectMapper()

    override fun store(sessionData: SessionData): Promise<Unit, Exception> = task {
        val serialized = sessionData.serialize(localDataEncryptionKey, localDataEncryptionParams)
        writeObjectToJsonFile(path, serialized)
    }

    override fun retrieve(): Promise<SessionData?, Exception> = task {
        retrieveSync()
    }

    override fun retrieveSync(): SessionData? {
        return readObjectFromJsonFile(path, SerializedSessionData::class.java)?.deserialize(localDataEncryptionKey, localDataEncryptionParams)
    }

    override fun delete(): Promise<Boolean, Exception> = task {
        path.delete()
    }
}