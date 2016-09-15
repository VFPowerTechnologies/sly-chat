package io.slychat.messenger.services

import io.slychat.messenger.core.UserId
import io.slychat.messenger.core.persistence.GroupId
import io.slychat.messenger.core.randomReceivedMessageInfo
import io.slychat.messenger.core.randomUserId
import io.slychat.messenger.services.messaging.ConversationMessage
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

fun randomConversationMessage(userId: UserId? = null, groupId: GroupId? = null): ConversationMessage {
    val user = userId ?: randomUserId()
    return if (groupId == null)
        ConversationMessage.Single(user, randomReceivedMessageInfo())
    else
        ConversationMessage.Group(groupId, user, randomReceivedMessageInfo())
}
