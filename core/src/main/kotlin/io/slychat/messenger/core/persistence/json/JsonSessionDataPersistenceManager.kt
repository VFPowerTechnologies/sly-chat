package io.slychat.messenger.core.persistence.json

import com.fasterxml.jackson.databind.ObjectMapper
import io.slychat.messenger.core.crypto.ciphers.EncryptionSpec
import io.slychat.messenger.core.crypto.ciphers.decryptData
import io.slychat.messenger.core.crypto.ciphers.encryptDataWithParams
import io.slychat.messenger.core.persistence.SessionData
import io.slychat.messenger.core.persistence.SessionDataPersistenceManager
import nl.komponents.kovenant.Promise
import nl.komponents.kovenant.task
import org.spongycastle.crypto.InvalidCipherTextException
import java.io.File
import java.io.FileNotFoundException

class JsonSessionDataPersistenceManager(
    val path: File,
    private val encryptionSpec: EncryptionSpec
) : SessionDataPersistenceManager {
    val objectMapper = ObjectMapper()

    override fun store(sessionData: SessionData): Promise<Unit, Exception> = task {
        val serialized = objectMapper.writeValueAsBytes(sessionData)

        val encrypted = encryptDataWithParams(encryptionSpec, serialized)

        path.writeBytes(encrypted.data)
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
            decryptData(encryptionSpec, encrypted)
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