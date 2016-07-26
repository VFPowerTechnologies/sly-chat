package io.slychat.messenger.services

import com.nhaarman.mockito_kotlin.mock
import com.nhaarman.mockito_kotlin.whenever
import io.slychat.messenger.core.persistence.ContactsPersistenceManager
import io.slychat.messenger.core.persistence.GroupPersistenceManager
import io.slychat.messenger.core.randomGroupId
import io.slychat.messenger.core.randomUserIds
import io.slychat.messenger.services.messaging.GroupEvent
import io.slychat.messenger.services.messaging.MessageProcessor
import io.slychat.messenger.testutils.testSubscriber
import org.assertj.core.api.Assertions.assertThat
import org.junit.Test
import rx.subjects.PublishSubject

class GroupServiceImplTest {
    val groupPersistenceManager: GroupPersistenceManager = mock()
    val messageProcessor: MessageProcessor = mock()
    val contactPersistenceManager: ContactsPersistenceManager = mock()

    val groupService = GroupServiceImpl(groupPersistenceManager, contactPersistenceManager, messageProcessor)

    @Test
    fun `it should proxy group events from MessageProcessor`() {
        val subject = PublishSubject.create<GroupEvent>()

        whenever(messageProcessor.groupEvents).thenReturn(subject)

        val testSubscriber = groupService.groupEvents.testSubscriber()

        val ev = GroupEvent.Joined(randomGroupId(), randomUserIds())
        subject.onNext(ev)

        val events = testSubscriber.onNextEvents

        assertThat(events).apply {
            `as`("Received group events")
            containsOnly(ev)
        }
    }
}