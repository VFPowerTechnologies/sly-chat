@file:JvmName("TestUtils")
package com.vfpowertech.keytap.core.test

import org.joda.time.DateTimeUtils
import java.io.File

fun createTempFile(suffix: String = ""): File =
    File.createTempFile("keytap-test", suffix)

/** Returns a temp file to be automatically deleted on vm shutdown. */
fun createTempFileWithAutoDelete(suffix: String = ""): File {
    val f = File.createTempFile("keytap-test", suffix)
    f.deleteOnExit()
    return f
}

inline fun <R> withTempFile(suffix: String = "", body: (File) -> R): R {
    val f = File.createTempFile("keytap-test", suffix)
    try {
        return body(f)
    }
    finally {
        f.delete()
    }
}

fun <R> withTimeAs(millis: Long, body: () -> R): R {
    DateTimeUtils.setCurrentMillisFixed(millis)
    return try {
        body()
    } finally {
        DateTimeUtils.setCurrentMillisSystem()
    }
}
