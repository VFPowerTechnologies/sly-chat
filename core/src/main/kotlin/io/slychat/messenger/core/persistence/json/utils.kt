@file:JvmName("JsonPersistenceUtils")
package io.slychat.messenger.core.persistence.json

import com.fasterxml.jackson.databind.ObjectMapper
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

