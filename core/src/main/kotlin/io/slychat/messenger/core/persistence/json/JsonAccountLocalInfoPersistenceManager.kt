package io.slychat.messenger.core.persistence.json

import com.fasterxml.jackson.databind.ObjectMapper
import io.slychat.messenger.core.crypto.DerivedKeySpec
import io.slychat.messenger.core.crypto.ciphers.decryptBulkData
import io.slychat.messenger.core.crypto.ciphers.encryptBulkData
import io.slychat.messenger.core.persistence.AccountLocalInfo
import io.slychat.messenger.core.persistence.AccountLocalInfoPersistenceManager
import nl.komponents.kovenant.Promise
import nl.komponents.kovenant.task
import org.spongycastle.crypto.InvalidCipherTextException
import java.io.File
import java.io.FileNotFoundException

class JsonAccountLocalInfoPersistenceManager(
    val path: File,
    private val derivedKeySpec: DerivedKeySpec
) : AccountLocalInfoPersistenceManager {
    private val objectMapper = ObjectMapper()

    override fun store(accountLocalInfo: AccountLocalInfo): Promise<Unit, Exception> = task {
        val serialized = objectMapper.writeValueAsBytes(accountLocalInfo)

        val encrypted = encryptBulkData(derivedKeySpec, serialized)

        path.writeBytes(encrypted)
    }

    override fun retrieveSync(): AccountLocalInfo? {
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

        return objectMapper.readValue(decrypted, AccountLocalInfo::class.java)
    }
}