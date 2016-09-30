package io.slychat.messenger.core.persistence.json

import com.fasterxml.jackson.databind.ObjectMapper
import io.slychat.messenger.core.crypto.HKDFInfoList
import io.slychat.messenger.core.crypto.ciphers.Key
import io.slychat.messenger.core.crypto.ciphers.decryptBulkData
import io.slychat.messenger.core.crypto.ciphers.encryptBulkData
import io.slychat.messenger.core.persistence.SessionData
import io.slychat.messenger.core.persistence.SessionDataPersistenceManager
import nl.komponents.kovenant.Promise
import nl.komponents.kovenant.task
import org.spongycastle.crypto.InvalidCipherTextException
import java.io.File
import java.io.FileNotFoundException

class JsonSessionDataPersistenceManager(
    val path: File,
    private val masterKey: Key
) : SessionDataPersistenceManager {
    private val objectMapper = ObjectMapper()

    override fun store(sessionData: SessionData): Promise<Unit, Exception> = task {
        val serialized = objectMapper.writeValueAsBytes(sessionData)

        val encrypted = encryptBulkData(masterKey, serialized, HKDFInfoList.jsonSessionData())

        path.writeBytes(encrypted)
    }

    override fun retrieve(): Promise<SessionData?, Exception> = task {
        retrieveSync()
    }

    override fun retrieveSync(): SessionData? {
        val encrypted = try {
            path.readBytes()
        }
        catch (e: FileNotFoundException) {
            return null
        }

        val decrypted = try {
            decryptBulkData(masterKey, encrypted, HKDFInfoList.jsonSessionData())
        }
        catch (e: InvalidCipherTextException) {
            return null
        }

        return objectMapper.readValue(decrypted, SessionData::class.java)
    }

    override fun delete(): Promise<Boolean, Exception> = task {
        path.delete()
    }
}