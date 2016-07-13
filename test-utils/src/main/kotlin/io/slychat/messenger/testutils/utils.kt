@file:JvmName("TestUtils")
package io.slychat.messenger.testutils

import nl.komponents.kovenant.Kovenant
import nl.komponents.kovenant.Promise
import org.assertj.core.api.Condition
import org.joda.time.DateTimeUtils
import org.mockito.invocation.InvocationOnMock
import org.mockito.stubbing.OngoingStubbing
import rx.Observable
import rx.observers.TestSubscriber
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

/** Convinence function for returning a successful promise. */
fun <T> OngoingStubbing<Promise<T, Exception>>.thenReturn(v: T) {
    this.thenReturn(Promise.ofSuccess(v))
}

/** Convinence function for returning a failed promise. */
fun <T> OngoingStubbing<Promise<T, Exception>>.thenReturn(e: Exception) {
    this.thenReturn(Promise.ofFail(e))
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

fun <T> cond(description: String, predicate: (T) -> Boolean): Condition<T> = object : Condition<T>(description) {
    override fun matches(value: T): Boolean = predicate(value)
}

fun <T> Observable<T>.testSubscriber(): TestSubscriber<T> {
    val testSubscriber = TestSubscriber<T>()

    this.subscribe(testSubscriber)

    return testSubscriber
}
