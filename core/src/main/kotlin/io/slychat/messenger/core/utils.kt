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
import javax.net.ssl.SSLHandshakeException

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

        //FIXME: use reflection to get at UIDevice.currentDevice().systemVersion(), as os.version isn't the iOS version
        osName == "Darwin" -> Os(Os.Type.IOS, osVersion)

        else -> Os(Os.Type.UNKNOWN, osVersion)
    }
}

val currentOs: Os = osFromProperties(
    System.getProperty("os.name"),
    System.getProperty("os.version")
)

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
    is SSLHandshakeException -> false
    is ConnectException -> false
    //not really sure if I should ignore all of these; haven't seen any that were of any value
    is SocketException -> false
    //android throws this from HttpURLConnection, wrapping the underlying ConnectException, etc
    else -> false
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
