package io.slychat.messenger.core.persistence.json

import com.fasterxml.jackson.databind.ObjectMapper
import io.slychat.messenger.core.crypto.ciphers.AES256GCMCipher
import io.slychat.messenger.core.crypto.ciphers.Key
import io.slychat.messenger.core.persistence.StartupInfo
import io.slychat.messenger.core.persistence.StartupInfoPersistenceManager
import nl.komponents.kovenant.Promise
import nl.komponents.kovenant.task
import java.io.File
import java.io.FileNotFoundException

//the encryption is just there for minor obfuscation purposes; this provides no real security
class JsonStartupInfoPersistenceManager(
    val path: File,
    private val encryptionKey: Key?
) : StartupInfoPersistenceManager {
    override fun store(startupInfo: StartupInfo): Promise<Unit, Exception> = task {
        val objectMapper = ObjectMapper()

        path.outputStream().use {
            val serialized = objectMapper.writeValueAsBytes(startupInfo)

            val data = if (encryptionKey != null) {
                val cipher = AES256GCMCipher()
                cipher.encrypt(encryptionKey, serialized)
            }
            else
                serialized

            it.write(data)
        }
    }

    override fun retrieve(): Promise<StartupInfo?, Exception> {
        return task {
            val bytes = try {
                path.readBytes()
            }
            catch (e: FileNotFoundException) {
                return@task null
            }

            if (bytes.isEmpty())
                return@task null

            val json = if (encryptionKey != null) {
                val cipher = AES256GCMCipher()
                cipher.decrypt(encryptionKey, bytes)
            }
            else
                bytes

            val objectMapper = ObjectMapper()

            objectMapper.readValue(json, StartupInfo::class.java)
        }
    }

    override fun delete(): Promise<Unit, Exception> = task {
        path.delete()
        Unit
    }
}