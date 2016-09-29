package io.slychat.messenger.core.persistence.json

import com.fasterxml.jackson.databind.ObjectMapper
import io.slychat.messenger.core.crypto.ciphers.EncryptionSpec
import io.slychat.messenger.core.crypto.ciphers.decryptData
import io.slychat.messenger.core.crypto.ciphers.encryptDataWithParams
import io.slychat.messenger.core.persistence.AccountParams
import io.slychat.messenger.core.persistence.AccountParamsPersistenceManager
import nl.komponents.kovenant.Promise
import nl.komponents.kovenant.task
import org.spongycastle.crypto.InvalidCipherTextException
import java.io.File
import java.io.FileNotFoundException

class JsonAccountParamsPersistenceManager(
    val path: File,
    private val encryptionSpec: EncryptionSpec
) : AccountParamsPersistenceManager {
    private val objectMapper = ObjectMapper()

    override fun store(accountParams: AccountParams): Promise<Unit, Exception> = task {
        val serialized = objectMapper.writeValueAsBytes(accountParams)

        val encrypted = encryptDataWithParams(encryptionSpec, serialized)

        path.writeBytes(encrypted.data)
    }

    override fun retrieveSync(): AccountParams? {
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

        return objectMapper.readValue(decrypted, AccountParams::class.java)
    }
}