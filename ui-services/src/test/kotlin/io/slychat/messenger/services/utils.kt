package io.slychat.messenger.services

import io.slychat.messenger.testutils.testSubscriber
import org.assertj.core.api.Assertions.assertThat
import rx.Observable
import rx.observers.TestSubscriber
import kotlin.test.assertTrue

fun <T> assertEventEmitted(testSubscriber: TestSubscriber<T>, asserter: (T) -> Unit) {
    val events = testSubscriber.onNextEvents

    assertTrue(events.isNotEmpty(), "No event emitted")

    val event = events.first()

    asserter(event)
}

fun <T> assertNoEventsEmitted(testSubscriber: TestSubscriber<T>) {
    val events = testSubscriber.onNextEvents

    assertThat(events)
        .`as`("Events")
        .isEmpty()
}

inline fun <reified T : Any, reified U : T> Observable<T>.subclassFilterTestSubscriber(): TestSubscriber<U> {
    return this.filter { it is U }.cast(U::class.java).testSubscriber()
}
