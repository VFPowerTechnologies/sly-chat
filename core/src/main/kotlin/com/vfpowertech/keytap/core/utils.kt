@file:JvmName("CoreUtils")
package com.vfpowertech.keytap.core

import com.fasterxml.jackson.core.type.TypeReference
import java.io.File
import java.io.FileOutputStream
import java.util.*

/**
 * Throw an IllegalArgumentException with the given message if the predicate is false.
 *
 * Mimics Scala's require().
 */
inline fun require(predicate: Boolean, message: String) {
    if (!predicate)
        throw IllegalArgumentException(message)
}

/** Shorthand for creating Jackson TypeRefrences. */
inline fun <reified T> typeRef(): TypeReference<T> = object : TypeReference<T>() {}

/** Read entire resource file as a UTF-8 text file. */
fun Class<*>.readResourceFileText(path: String): String =
    getResourceAsStream(path).bufferedReader().use {
        it.readText()
    }

/** Get the expected platform file name for a shared library. */
fun getSharedLibFileName(base: String): String {
    val os = System.getProperty("os.name")
    return when {
        os == "Linux" -> "lib${base}.so"
        os.startsWith("Windows") -> "${base}.dll"
    //TODO osx
        else -> throw UnsupportedOperationException("Unsupported OS: $os")
    }
}

//this is in core to simplify testing; should only be used by the desktop port
/** Attempts to unpack and load a shared library from resources. Do not use on android. */
fun Class<*>.loadSharedLibFromResource(base: String) {
    val platformName = getSharedLibFileName(base)

    val inputStream = getResourceAsStream("/$platformName")
    if (inputStream == null)
        throw UnsatisfiedLinkError("Unable to find shared library $platformName")

    val path = File.createTempFile("sqlitetest", "")
    path.deleteOnExit()
    //according to the JNA src code, ext .dll is required for loading on windows
    inputStream.use { sharedLibStream ->
        FileOutputStream(path).use {
            val buffer = ByteArray(4096)
            while (sharedLibStream.read(buffer) > 0)
                it.write(buffer)
        }
    }

    System.load(path.toString())
}

/** Simple path builder helper. */
operator fun File.div(child: String): File =
    File(this, child)

operator fun String.div(child: String): File =
    File(this, child)

/** Returns a random UUID as a string, without dashes. */
fun randomUUID(): String =
    UUID.randomUUID().toString().replace("-", "")
