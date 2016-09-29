package io.slychat.messenger.services.config

import io.slychat.messenger.core.crypto.ciphers.EncryptionSpec
import io.slychat.messenger.core.crypto.ciphers.decryptBulkData
import io.slychat.messenger.core.crypto.ciphers.encryptDataWithParams
import org.slf4j.LoggerFactory
import java.io.File
import java.io.FileNotFoundException

//The reason we just this over Input/OutputStream and CipherInput/OutputStream is that we since we use bouncy castle's
//API directly, we can't use Cipher*Stream. It's also much easier to just mock a simple API like this.
/** The read and write functions may be called concurrently. */
interface ConfigStorage {
    fun write(data: ByteArray)
    fun read(): ByteArray?
}

class FileConfigStorage(private val path: File) : ConfigStorage {
    private val log = LoggerFactory.getLogger(javaClass)

    override fun write(data: ByteArray) {
        log.info("Writing config file {}", path)
        try {
            path.outputStream().use {
                it.write(data)
            }
            log.info("Successful wrote config file {}", path)
        }
        catch (t: Throwable) {
            log.error("Unable to write {}: {}", path, t.message, t)
        }
    }

    override fun read(): ByteArray? {
        return try {
            path.inputStream().use { it.readBytes() }
        }
        catch (e: FileNotFoundException) {
            null
        }
        catch (e: Exception) {
            log.warn("Unable to load config file {}: {}", path, e.message)
            null
        }
    }

}

class CipherConfigStorageFilter(
    private val encryptionSpec: EncryptionSpec,
    private val underlying: ConfigStorage
) : ConfigStorage {
    override fun write(data: ByteArray) {
        underlying.write(encryptDataWithParams(encryptionSpec, data).data)
    }

    override fun read(): ByteArray? {
        val cipherText = underlying.read()
        return if (cipherText != null)
            decryptBulkData(encryptionSpec, cipherText)
        else
            null
    }
}
