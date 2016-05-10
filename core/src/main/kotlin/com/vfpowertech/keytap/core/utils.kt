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

private val base64chars = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/"
//https://en.wikibooks.org/wiki/Algorithm_Implementation/Miscellaneous/Base64#Java
//converted from the above into kotlin
fun base64encode(bytes: ByteArray): String {
    val builder = StringBuilder()

    val mod = bytes.size % 3
    val padCount = if (mod > 0) 3 - mod else 0

    val paddedBytes = if (padCount > 0) {
        val paddedBytes = ByteArray(bytes.size + padCount)
        System.arraycopy(bytes, 0, paddedBytes, 0, bytes.size)
        paddedBytes
    }
    else
        bytes

    for (i in 0..paddedBytes.size-1 step 3) {
        //MIME spec says we should add newlines after every 76 chars
        //but since we don't actually use this for MIME data we don't do this
        //we also don't have a trailing newline

        val n = (paddedBytes[i].toLong() shl 16) + (paddedBytes[i+1].toLong() shl 8) + (paddedBytes[i+2])

        val n1 = (n shr 18) and 63
        val n2 = (n shr 12) and 63
        val n3 = (n shr 6) and 63
        val n4 = n and 63

        builder.append(base64chars[n1.toInt()])
        builder.append(base64chars[n2.toInt()])
        builder.append(base64chars[n3.toInt()])
        builder.append(base64chars[n4.toInt()])
    }

    if (padCount > 0) {
        for (i in 0..padCount-1)
            builder.deleteCharAt(builder.length-1)
    }

    return builder.toString() + "=".repeat(padCount)
}
