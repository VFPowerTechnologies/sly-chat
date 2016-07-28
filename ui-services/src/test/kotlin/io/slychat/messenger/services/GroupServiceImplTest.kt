package io.slychat.messenger.services

import com.nhaarman.mockito_kotlin.any
import com.nhaarman.mockito_kotlin.mock
import com.nhaarman.mockito_kotlin.whenever
import io.slychat.messenger.core.persistence.ContactsPersistenceManager
import io.slychat.messenger.core.persistence.GroupMembershipLevel
import io.slychat.messenger.core.persistence.GroupPersistenceManager
import io.slychat.messenger.core.randomGroupInfo
import io.slychat.messenger.core.randomUserId
import io.slychat.messenger.core.randomUserIds
import io.slychat.messenger.services.messaging.GroupEvent
import io.slychat.messenger.testutils.KovenantTestModeRule
import io.slychat.messenger.testutils.thenAnswerWithArg
import io.slychat.messenger.testutils.thenReturn
import org.junit.Before
import org.junit.ClassRule
import org.junit.Test
import rx.observers.TestSubscriber
import kotlin.test.assertEquals

class GroupServiceImplTest {
    companion object {
        @JvmField
        @ClassRule
        val kovenantTestMode = KovenantTestModeRule()
    }

    val groupPersistenceManager: GroupPersistenceManager = mock()
    val contactPersistenceManager: ContactsPersistenceManager = mock()

    val groupService = GroupServiceImpl(groupPersistenceManager, contactPersistenceManager)

    @Before
    fun before() {
        whenever(groupPersistenceManager.removeMember(any(), any())).thenReturn(true)
        whenever(groupPersistenceManager.addMembers(any(), any())).thenAnswerWithArg(1)
        whenever(groupPersistenceManager.join(any(), any())).thenReturn(Unit)
    }

    inline fun <reified T : GroupEvent> groupEventCollectorFor(): TestSubscriber<T> {
        return groupService.groupEvents.subclassFilterTestSubscriber()
    }

    @Test
    fun `it should emit a NewGroup event when joining a new group`() {
        val groupInfo = randomGroupInfo()
        val members = randomUserIds()

        val testSubscriber = groupEventCollectorFor<GroupEvent.NewGroup>()

        groupService.join(groupInfo, members).get()

        assertEventEmitted(testSubscriber) { ev ->
            assertEquals(groupInfo.id, ev.id, "Invalid id")
            assertEquals(members, ev.members, "Invalid member list")
        }
    }

    fun testJoinEvent(shouldEventBeEmitted: Boolean) {
        val newMember = randomUserId()
        val groupInfo = randomGroupInfo(GroupMembershipLevel.JOINED)

        val testSubscriber = groupEventCollectorFor<GroupEvent.Joined>()

        groupService.addMembers(groupInfo.id, setOf(newMember))

        if (shouldEventBeEmitted) {
            assertEventEmitted(testSubscriber) { event ->
                assertEquals(groupInfo.id, event.id, "Invalid group id")
                assertEquals(setOf(newMember), event.newMembers, "Invalid new member id")
            }
        }
        else
            assertNoEventsEmitted(testSubscriber)
    }

    @Test
    fun `it should emit a Joined event when adding a new member`() {
        whenever(groupPersistenceManager.addMembers(any(), any())).thenAnswerWithArg(1)
        testJoinEvent(true)
    }

    @Test
    fun `it should emit a Joined event when adding a duplicate member`() {
        whenever(groupPersistenceManager.addMembers(any(), any())).thenReturn(emptySet())
        testJoinEvent(false)
    }

    fun testPartEvent(shouldEventBeEmitted: Boolean) {
        val sender = randomUserId()
        val groupInfo = randomGroupInfo(GroupMembershipLevel.JOINED)

        val testSubscriber = groupEventCollectorFor<GroupEvent.Parted>()

        groupService.removeMember(groupInfo.id, sender)

        if (shouldEventBeEmitted) {
            assertEventEmitted(testSubscriber) { event ->
                assertEquals(groupInfo.id, event.id, "Invalid group id")
                assertEquals(sender, event.member, "Invalid new member id")
            }
        }
        else
            assertNoEventsEmitted(testSubscriber)
    }

    @Test
    fun `it should emit a Parted event when removing a member`() {
        whenever(groupPersistenceManager.removeMember(any(), any())).thenReturn(true)
        testPartEvent(true)
    }

    @Test
    fun `it not should emit a Parted event when removing a non-existent member`() {
        whenever(groupPersistenceManager.removeMember(any(), any())).thenReturn(false)
        testPartEvent(false)
    }
}