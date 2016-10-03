package io.slychat.messenger.core.persistence.json

import com.fasterxml.jackson.databind.ObjectMapper
import io.slychat.messenger.core.crypto.DerivedKeySpec
import io.slychat.messenger.core.crypto.ciphers.decryptBulkData
import io.slychat.messenger.core.crypto.ciphers.encryptBulkData
import io.slychat.messenger.core.persistence.AccountParams
import io.slychat.messenger.core.persistence.AccountParamsPersistenceManager
import nl.komponents.kovenant.Promise
import nl.komponents.kovenant.task
import org.spongycastle.crypto.InvalidCipherTextException
import java.io.File
import java.io.FileNotFoundException

class JsonAccountParamsPersistenceManager(
    val path: File,
    private val derivedKeySpec: DerivedKeySpec
) : AccountParamsPersistenceManager {
    private val objectMapper = ObjectMapper()

    override fun store(accountParams: AccountParams): Promise<Unit, Exception> = task {
        val serialized = objectMapper.writeValueAsBytes(accountParams)

        val encrypted = encryptBulkData(derivedKeySpec, serialized)

        path.writeBytes(encrypted)
    }

    override fun retrieveSync(): AccountParams? {
        val encrypted = try {
            path.readBytes()
        }
        catch (e: FileNotFoundException) {
            return null
        }

        val decrypted = try {
            decryptBulkData(derivedKeySpec, encrypted)
        }
        catch (e: InvalidCipherTextException) {
            return null
        }

        return objectMapper.readValue(decrypted, AccountParams::class.java)
    }
}