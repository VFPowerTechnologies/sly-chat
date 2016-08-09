@file:JvmName("CoreUtils")
package io.slychat.messenger.core

import com.fasterxml.jackson.core.type.TypeReference
import io.slychat.messenger.core.crypto.hexify
import org.joda.time.DateTime
import java.io.File
import java.security.SecureRandom
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
    return when (currentOs.type) {
        Os.Type.LINUX -> "lib$base.so"
        Os.Type.WINDOWS -> "$base.dll"
        Os.Type.OSX -> "lib$base.dylib"
        else -> throw UnsupportedOsException(currentOs)
    }
}

//this is in core to simplify testing; should only be used by the desktop port
/** Attempts to unpack and load a shared library from resources. Do not use on android. */
fun Class<*>.loadSharedLibFromResource(base: String) {
    val libName = getSharedLibFileName(base)

    //.dll suffix is required for loading on windows, else a UnsatisfiedLinkError("Can't find dependent libraries") is thrown
    val suffix = if (currentOs.type == Os.Type.WINDOWS) ".dll" else ""

    val path = File.createTempFile("sqlitetest", suffix)
    path.deleteOnExit()

    val inputStream = getResourceAsStream("/$libName") ?: throw UnsatisfiedLinkError("Unable to find shared library $libName")

    inputStream.use { sharedLibStream ->
        path.outputStream().use {
            inputStream.copyTo(it)
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
fun randomUUID(): String {
    val bytes = ByteArray(16)
    SecureRandom().nextBytes(bytes)
    return bytes.hexify()
}

/** Returns the current time in milliseconds. */
fun currentTimestamp(): Long = DateTime().millis

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

inline fun <T, R> Iterable<T>.mapToSet(transform: (T) -> R): Set<R> {
    return mapTo(HashSet<R>(), transform)
}

inline fun <T, K, V> Iterable<T>.mapToMap(transform: (T) -> Pair<K, V>): Map<K, V> {
    val m = HashMap<K, V>()

    forEach {
        val pair = transform(it)
        m.put(pair.first, pair.second)
    }

    return m
}

/** Attempts to return the current Android version. Uses reflection to access android.os.Build.VERSION.RELEASE. */
private fun getAndroidVersion(): String? {
    return try {
        //android.os.Build(class).VERSION(static class).RELEASE(static string)
        //we could technically use android.os.Build$VERSION, but afaik the $ naming is convention and not standard so
        //I'd rather not rely on it
        val buildClass = Class.forName("android.os.Build")
        val versionClass = buildClass.declaredClasses.filter { it.simpleName == "VERSION" }.firstOrNull()
        if (versionClass == null)
            "unknown"
        else {
            val releaseField = versionClass.getField("RELEASE")
            releaseField.get(null) as String
        }
    }
    catch (e: ClassNotFoundException) {
        null
    }
}

/**
 * Used to determine OS info from Java's os.name and os.version system properties. Currently only tested on the following JREs: Oracle, OpenJDK, Android
 *
 * Runtime name is required for detecting Android, as os.name simply returns Linux
 */
fun osFromProperties(osName: String, osVersion: String): Os {
    return when {
        osName == "Linux" -> {
            //no property actually contains the android version, and os.version is just the linux version
            //so we just use reflection for testing
            val androidVersion = getAndroidVersion()
            if (androidVersion != null)
                Os(Os.Type.ANDROID, androidVersion)
            else
                Os(Os.Type.LINUX, osVersion)
        }

        osName.startsWith("Windows") -> {
            //osVersion for windows returns values like 6.0 (vista), 6.1 (7), 6.2 (8), etc
            //it's nicer for display/error reporting to just use the user facing version instead
            //os.name is always "Windows <version>" on the tested runtimes
            val parts = osName.split(" ", limit = 2)
            Os(Os.Type.WINDOWS, parts[1])
        }

        osName == "Mac OS X" -> Os(Os.Type.OSX, osVersion)

        else -> Os(Os.Type.UNKNOWN, osVersion)
    }
}

val currentOs: Os = osFromProperties(
    System.getProperty("os.name"),
    System.getProperty("os.version")
)
