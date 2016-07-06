@file:JvmName("TestUtils")
package io.slychat.messenger.testutils

import nl.komponents.kovenant.Kovenant
import org.joda.time.DateTimeUtils
import java.io.File

inline fun <R> withTempFile(suffix: String = "", body: (File) -> R): R {
    val f = File.createTempFile("sly-test", suffix)
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
    }
    finally {
        DateTimeUtils.setCurrentMillisSystem()
    }
}

fun <R> withKovenantThreadedContext(body: () -> R): R {
    val savedContext = Kovenant.context
    Kovenant.context = Kovenant.createContext {}
    return try {
        body()
    }
    finally {
        Kovenant.context = savedContext
    }
}
