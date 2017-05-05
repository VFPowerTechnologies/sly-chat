@file:JvmName("TestUtils")
package io.slychat.messenger.testutils

import nl.komponents.kovenant.Kovenant
import nl.komponents.kovenant.Promise
import org.assertj.core.api.AbstractAssert
import org.assertj.core.api.Condition
import org.joda.time.DateTimeUtils
import org.mockito.invocation.InvocationOnMock
import org.mockito.stubbing.OngoingStubbing
import rx.Observable
import rx.observers.TestSubscriber
import rx.plugins.RxJavaHooks
import java.io.File
import java.util.*

inline fun <R> withTempFile(suffix: String = "", body: (File) -> R): R {
    val f = File.createTempFile("sly-test", suffix)
    try {
        return body(f)
    }
    finally {
        f.delete()
    }
}

fun randomString(length: Int): String {
    val choices = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789"

    val r = Random()

    val b = StringBuilder()

    (0..length - 1).forEach {
        val i = r.nextInt(choices.length - 1)
        b.append(choices[i])
    }

    return b.toString()
}

//XXX copied from core since we can't sanely share this
private fun recursivelyDeleteDir(dir: File) {
    if (!dir.exists())
        return

    require(dir.isDirectory) { "$dir is not a directory" }

    val dirs = ArrayDeque<File>()
    dirs.add(dir)

    while (dirs.isNotEmpty()) {
        val path = dirs.first
        val contents = path.listFiles()

        if (contents.isNotEmpty()) {
            contents.forEach { file ->
                if (file.isDirectory) {
                    dirs.addFirst(file)
                }
                else if (file.isFile) {
                    file.delete()
                }
                else
                    throw RuntimeException("$file is not a file or directory")
            }
        }
        else {
            path.delete()
            dirs.pop()
        }
    }
}

fun <R> withTempDir(body: (dirPath: File) -> R): R {
    val property = System.getProperty("java.io.tmpdir")
    if (property.isBlank())
        throw RuntimeException("java.io.tmpdir is blank")

    val tempDir = File(property)

    val suffix = randomString(5)

    val dir = File(tempDir, "slytest-$suffix")

    dir.mkdirs()

    return try {
        body(dir)
    }
    finally {
        recursivelyDeleteDir(dir)
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

fun <R> withRxHooks(body: () -> R): R {
    return try {
        body()
    }
    finally {
        RxJavaHooks.reset()
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

/** Convinence function for returning a successful promise. */
fun <T> OngoingStubbing<Promise<T, Exception>>.thenResolve(v: T): OngoingStubbing<Promise<T, Exception>> {
    return this.thenReturn(Promise.ofSuccess(v))
}

fun OngoingStubbing<Promise<Unit, Exception>>.thenResolveUnit(): OngoingStubbing<Promise<Unit, Exception>> {
    return this.thenReturn(Promise.ofSuccess(Unit))
}

/** Convinence function for returning a failed promise. */
fun <T> OngoingStubbing<Promise<T, Exception>>.thenReject(e: Exception): OngoingStubbing<Promise<T, Exception>> {
    return this.thenReturn(Promise.ofFail(e))
}

fun <T> OngoingStubbing<Promise<T, Exception>>.thenAnswerSuccess(body: (InvocationOnMock) -> T) {
    this.thenAnswer {
        Promise.ofSuccess<T, Exception>(body(it))
    }
}

fun <T> OngoingStubbing<Promise<T, Exception>>.thenAnswerFailure(body: (InvocationOnMock) -> Exception) {
    this.thenAnswer {
        Promise.ofFail<T, Exception>(body(it))
    }
}

inline fun <reified T> OngoingStubbing<Promise<T, Exception>>.thenAnswerWithArg(n: Int) {
    this.thenAnswerSuccess {
        it.arguments[n] as T
    }
}

fun <T> cond(description: String, predicate: (T) -> Boolean): Condition<T> = object : Condition<T>(description) {
    override fun matches(value: T): Boolean = predicate(value)
}

fun <T> Observable<T>.testSubscriber(): TestSubscriber<T> {
    val testSubscriber = TestSubscriber<T>()

    this.subscribe(testSubscriber)

    return testSubscriber
}

inline fun <S : AbstractAssert<*, A>, A> S.desc(description: String, body: S.() -> Unit) {
    describedAs(description)
    this.body()
}

