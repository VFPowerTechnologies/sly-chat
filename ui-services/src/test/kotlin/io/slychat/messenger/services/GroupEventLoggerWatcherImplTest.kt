package io.slychat.messenger.services

import io.slychat.messenger.core.persistence.GroupEventData
import io.slychat.messenger.core.persistence.GroupMembershipLevel
import io.slychat.messenger.core.persistence.LogEvent
import io.slychat.messenger.core.persistence.LogTarget
import io.slychat.messenger.core.randomGroupId
import io.slychat.messenger.core.randomGroupName
import io.slychat.messenger.core.randomUserIds
import io.slychat.messenger.services.messaging.GroupEvent
import io.slychat.messenger.testutils.withTimeAs
import org.assertj.core.api.Assertions.assertThat
import org.junit.Test
import rx.subjects.PublishSubject

class GroupEventLoggerWatcherImplTest {
    private val eventLogService = MockEventLogService()
    private val groupEvents = PublishSubject.create<GroupEvent>()
    @Suppress("unused")
    private val groupEventLoggerWatcher = GroupEventLoggerWatcherImpl(groupEvents, eventLogService)

    @Test
    fun `it should log MembershipLevelChange and MemberChange when receiving a Joined event`() {
        val id = randomGroupId()
        val members = randomUserIds()

        val currentTime = 1L
        withTimeAs(currentTime) {
            groupEvents.onNext(GroupEvent.Joined(id, randomGroupName(), members, false))
        }

        val target = LogTarget.Conversation(id)
        val expected = listOf(
            LogEvent.Group(
                target,
                currentTime,
                GroupEventData.MembershipLevelChange(id, GroupMembershipLevel.JOINED)
            ),
            LogEvent.Group(
                target,
                currentTime,
                GroupEventData.MemberChange(id, members, emptySet())
            )
        )

        assertThat(eventLogService.loggedEvents).apply {
            `as`("Should include membership level and member change events")
            containsAll(expected)
        }
    }

    @Test
    fun `it should log MembershipLevelChange when receiving a Parted event`() {
        val id = randomGroupId()

        val currentTime = 1L
        withTimeAs(currentTime) {
            groupEvents.onNext(GroupEvent.Parted(id, false))
        }

        val target = LogTarget.Conversation(id)
        val expected = LogEvent.Group(
            target,
            currentTime,
            GroupEventData.MembershipLevelChange(id, GroupMembershipLevel.PARTED)
        )

        assertThat(eventLogService.loggedEvents).apply {
            `as`("Should include membership level change event")
            containsOnly(expected)
        }
    }

    @Test
    fun `it should log MembershipLevelChange when receiving a Blocked event`() {
        val id = randomGroupId()

        val currentTime = 1L
        withTimeAs(currentTime) {
            groupEvents.onNext(GroupEvent.Blocked(id, false))
        }

        val target = LogTarget.Conversation(id)
        val expected = LogEvent.Group(
            target,
            currentTime,
            GroupEventData.MembershipLevelChange(id, GroupMembershipLevel.BLOCKED)
        )

        assertThat(eventLogService.loggedEvents).apply {
            `as`("Should include membership level change event")
            containsOnly(expected)
        }
    }

    @Test
    fun `it should log MemberChange when receiving a MembershipChanged event`() {
        val id = randomGroupId()
        val newMembers = randomUserIds()
        val partedMembers = randomUserIds()

        val currentTime = 1L
        withTimeAs(currentTime) {
            groupEvents.onNext(GroupEvent.MembershipChanged(id, newMembers, partedMembers, false))
        }

        val target = LogTarget.Conversation(id)
        val expected = LogEvent.Group(
            target,
            currentTime,
            GroupEventData.MemberChange(id, newMembers, partedMembers)
        )

        assertThat(eventLogService.loggedEvents).apply {
            `as`("Should include member change event")
            containsOnly(expected)
        }
    }
}