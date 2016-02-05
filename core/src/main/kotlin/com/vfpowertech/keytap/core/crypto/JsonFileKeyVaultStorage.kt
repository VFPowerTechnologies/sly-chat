package com.vfpowertech.keytap.core.crypto

import com.fasterxml.jackson.databind.ObjectMapper
import com.vfpowertech.keytap.core.http.slurpInputStreamReader
import java.io.BufferedReader
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.InputStreamReader

/** Implementation of a KeyVaultStorage that stores its data in a json file on disk. */
class JsonFileKeyVaultStorage(val path: File) : KeyVaultStorage {
    private val objectMapper = ObjectMapper()

    override fun read(): SerializedKeyVault {
        val json = BufferedReader(InputStreamReader(FileInputStream(path), "UTF-8")).use { slurpInputStreamReader(it) }
        return objectMapper.readValue(json, SerializedKeyVault::class.java)
    }

    override fun write(serializedKeyVault: SerializedKeyVault) {
        val jsonBytes = objectMapper.writeValueAsBytes(serializedKeyVault)
        FileOutputStream(path).use { it.write(jsonBytes) }
    }
}