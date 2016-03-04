package com.vfpowertech.keytap.core.persistence.json

import com.fasterxml.jackson.databind.ObjectMapper
import com.vfpowertech.keytap.core.SessionDataPersistenceManager
import com.vfpowertech.keytap.core.crypto.ciphers.CipherParams
import com.vfpowertech.keytap.core.persistence.SerializedSessionData
import com.vfpowertech.keytap.core.persistence.SessionData
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
        path.outputStream().use {
            it.write(objectMapper.writeValueAsBytes(sessionData.serialize(localDataEncryptionKey, localDataEncryptionParams)))
        }
    }

    override fun retrieve(): Promise<SessionData, Exception> = task {
        val bytes = path.inputStream().use {
            it.readBytes()
        }

        objectMapper.readValue(bytes, SerializedSessionData::class.java).deserialize(localDataEncryptionKey, localDataEncryptionParams)
    }
}