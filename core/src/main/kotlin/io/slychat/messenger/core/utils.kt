@file:JvmName("CoreUtils")
package io.slychat.messenger.core

import com.fasterxml.jackson.core.type.TypeReference
import org.joda.time.DateTime
import java.io.File
import java.net.ConnectException
import java.net.SocketException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.util.*
import javax.net.ssl.SSLException

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

    val path = File.createTempFile("slychat", suffix)
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

/** Returns the current time in milliseconds. */
fun currentTimestamp(): Long = DateTime().millis

fun currentTimestampSeconds(): Long = DateTime().millis / 1000

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

inline fun <T, R> Iterable<T>.filterMap(transform: (T) -> R?): List<R> {
    val r = ArrayList<R>()

    for (e in this) {
        val v = transform(e)
        if (v != null)
            r.add(v)
    }

    return r
}

operator fun <K, V> MutableMap<K, V>.minusAssign(key: K) {
    remove(key)
}

operator fun <K, V> Map<K, V>.plus(entry: Pair<K, V>): Map<K, V> {
    val m = HashMap(this)

    m += entry

    return m
}

operator fun <K, V> Map<K, V>.minus(key: K): Map<K, V> {
    val m = HashMap(this)

    m.remove(key)

    return m
}

/**
 * Used to determine OS info from Java's os.name and os.version system properties. Currently only tested on the following JREs: Oracle, OpenJDK, Android
 *
 * Runtime name is required for detecting Android, as os.name simply returns Linux
 */
fun osFromProperties(osName: String, osVersion: String): Os {
    return when {
        osName == "Linux" -> {
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

//default to desktop lookup if no special class is provided
private fun getOsInfo(): Os {
    val cls = try {
        Class.forName("io.slychat.messenger.core.OSInfo")
    }
    catch (e: ClassNotFoundException) {
        return osFromProperties(
            System.getProperty("os.name"),
            System.getProperty("os.version")
        )
    }

    val getType = cls.getMethod("getType")
    val osType = getType.invoke(null) as Os.Type

    val getVersion = cls.getMethod("getVersion")
    val version = getVersion.invoke(null) as String

    return Os(osType, version)
}

val currentOs: Os = getOsInfo()

fun emptyByteArray(): ByteArray = ByteArray(0)

fun ByteArray.hexify(): String =
    this.joinToString("") { "%02x".format(it) }

fun String.unhexify(): ByteArray {
    require((length % 2) == 0, "String length must be a multiple of 2")

    val bytes = ByteArray(length / 2)

    for (i in 0..bytes.size-1) {
        val v = this.subSequence(i*2, (i*2)+2).toString()
        bytes[i] = Integer.parseInt(v, 16).toByte()
    }

    return bytes
}

/** Returns true if given exception is not a network error. */
fun isNotNetworkError(t: Throwable): Boolean = when (t) {
    is SocketTimeoutException -> false
    is UnknownHostException -> false
    is SSLException -> false
    is ConnectException -> false
    //android throws this from HttpURLConnection, wrapping the underlying ConnectException, etc
    //not really sure if I should ignore all of these; haven't seen any that were of any value
    is SocketException -> false
    else -> true
}

/** Logs at error level only if isError is true; otherwise logs at warning level. */
//this is inlined as to not show up in stacktraces... however this clobbers the stacktrace for the call site
//in this case we just get the offending file, even if the line number is invalid (function name is fine though)
//this is still somewhat easier than just see `condError` as the result of a bunch of errors though
@Suppress("NOTHING_TO_INLINE")
inline fun org.slf4j.Logger.condError(isError: Boolean, format: String, vararg args: Any?) {
    if (isError)
        this.error(format, *args)
    else
        this.warn(format, *args)
}

/** No-op used to enforce compile-time exhaustive matching of a side-effecting when expression. */
@Suppress("NOTHING_TO_INLINE", "unused")
inline fun Any?.enforceExhaustive() {}
