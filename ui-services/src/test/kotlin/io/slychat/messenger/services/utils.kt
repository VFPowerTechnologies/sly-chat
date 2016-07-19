package io.slychat.messenger.services

import io.slychat.messenger.core.UserId
import io.slychat.messenger.core.mapToSet
import io.slychat.messenger.core.persistence.*
import io.slychat.messenger.core.randomUUID
import io.slychat.messenger.testutils.testSubscriber
import org.assertj.core.api.Assertions.assertThat
import rx.Observable
import rx.observers.TestSubscriber
import java.util.*
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

fun randomGroupInfo(): GroupInfo = randomGroupInfo(false, GroupMembershipLevel.JOINED)
fun randomGroupInfo(isPending: Boolean, membershipLevel: GroupMembershipLevel): GroupInfo =
    GroupInfo(randomGroupId(), randomGroupName(), isPending, membershipLevel)

fun randomGroupName(): String = randomUUID()

fun randomGroupMembers(n: Int = 2): Set<UserId> = (1..n).mapTo(HashSet()) { randomUserId() }

fun randomUserId(): UserId {
    val l = 1 + Random().nextInt(10000-1) + 1
    return UserId(l.toLong())
}

fun randomUserIds(n: Int = 2): Set<UserId> = (1..n).mapToSet { randomUserId() }

fun randomGroupId(): GroupId = GroupId(randomUUID())

fun randomMessageId(): String = randomUUID()

fun randomTextGroupMetaData(groupId: GroupId? = null): MessageMetadata {
    return MessageMetadata(
        randomUserId(),
        groupId ?: randomGroupId(),
        MessageCategory.TEXT_GROUP,
        randomMessageId()
    )
}
