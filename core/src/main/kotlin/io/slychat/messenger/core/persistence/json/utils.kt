@file:JvmName("JsonPersistenceUtils")
package io.slychat.messenger.core.persistence.json

import com.fasterxml.jackson.core.JsonProcessingException
import com.fasterxml.jackson.databind.ObjectMapper
import io.slychat.messenger.core.crypto.DerivedKeySpec
import io.slychat.messenger.core.crypto.ciphers.UnknownCipherException
import io.slychat.messenger.core.crypto.ciphers.decryptBulkData
import io.slychat.messenger.core.crypto.ciphers.encryptBulkData
import org.spongycastle.crypto.InvalidCipherTextException
import java.io.File
import java.io.FileNotFoundException

/** Writes the given object as JSON to the given file path. */
fun writeObjectToJsonFile(path: File, o: Any) {
    val objectMapper = ObjectMapper()
    path.outputStream().use {
        it.write(objectMapper.writeValueAsBytes(o))
    }
}

/**
 * Attempts to read an object from the given path.
 *
 * If the file could not be found, or the contents are empty, returns null.
 */
fun <T> readObjectFromJsonFile(path: File, clazz: Class<T>): T? {
    val json = try {
        path.readText()
    }
    catch (e: FileNotFoundException) {
        return null
    }

    if (json.isEmpty())
        return null

    val objectMapper = ObjectMapper()

    return objectMapper.readValue(json, clazz)
}

fun <T> writeEncryptedObjectToJsonFile(path: File, derivedKeySpec: DerivedKeySpec, obj: T) {
    val serialized = JSONMapper.mapper.writeValueAsBytes(obj)

    val encrypted = encryptBulkData(derivedKeySpec, serialized)

    path.writeBytes(encrypted)
}

fun <T> readEncryptedObjectFromJsonFile(path: File, derivedKeySpec: DerivedKeySpec, clazz: Class<T>): T? {
    val encrypted = try {
        path.readBytes()
    }
    catch (e: FileNotFoundException) {
        return null
    }

    val decrypted = try {
        decryptBulkData(derivedKeySpec, encrypted)
    }
    catch (e: UnknownCipherException) {
        null
    }
    catch (e: InvalidCipherTextException) {
        null
    }

    return try {
        decrypted?.let { JSONMapper.mapper.readValue(it, clazz) }
    }
    catch (e: JsonProcessingException) {
        null
    }
}