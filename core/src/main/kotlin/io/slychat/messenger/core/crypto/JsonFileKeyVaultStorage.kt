package io.slychat.messenger.core.crypto

import com.fasterxml.jackson.databind.ObjectMapper
import java.io.*

/** Implementation of a KeyVaultStorage that stores its data in a json file on disk. */
class JsonFileKeyVaultStorage(val path: File) : KeyVaultStorage {
    private val objectMapper = ObjectMapper()

    override fun read(): SerializedKeyVault {
        val json = BufferedReader(InputStreamReader(FileInputStream(path), "UTF-8")).use { it.readText() }
        return objectMapper.readValue(json, SerializedKeyVault::class.java)
    }

    override fun write(serializedKeyVault: SerializedKeyVault) {
        val jsonBytes = objectMapper.writeValueAsBytes(serializedKeyVault)
        FileOutputStream(path).use { it.write(jsonBytes) }
    }
}