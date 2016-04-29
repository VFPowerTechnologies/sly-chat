package com.vfpowertech.keytap.core.persistence.json

import com.fasterxml.jackson.databind.ObjectMapper
import com.vfpowertech.keytap.core.crypto.ciphers.CipherParams
import com.vfpowertech.keytap.core.persistence.SerializedSessionData
import com.vfpowertech.keytap.core.persistence.SessionData
import com.vfpowertech.keytap.core.persistence.SessionDataPersistenceManager
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

    override fun retrieve(): Promise<SessionData, Exception> = task {
        retrieveSync()
    }

    fun retrieveSync(): SessionData {
        val bytes = path.inputStream().use {
            it.readBytes()
        }

        return objectMapper.readValue(bytes, SerializedSessionData::class.java).deserialize(localDataEncryptionKey, localDataEncryptionParams)
    }
}