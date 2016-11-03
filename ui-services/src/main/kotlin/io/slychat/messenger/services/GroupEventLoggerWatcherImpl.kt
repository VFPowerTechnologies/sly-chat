package io.slychat.messenger.services

import io.slychat.messenger.core.currentTimestamp
import io.slychat.messenger.core.persistence.GroupEventData
import io.slychat.messenger.core.persistence.GroupMembershipLevel
import io.slychat.messenger.core.persistence.LogEvent
import io.slychat.messenger.core.persistence.LogTarget
import io.slychat.messenger.services.messaging.GroupEvent
import rx.Observable
import rx.Subscription
import java.util.*

class GroupEventLoggerWatcherImpl(
    groupEvents: Observable<GroupEvent>,
    private val eventLogService: EventLogService
) : GroupEventLoggerWatcher {
    private var subscription: Subscription? = null

    init {
        subscription = groupEvents.subscribe { onGroupEvent(it) }
    }

    private fun onGroupEvent(event: GroupEvent) {
        val data = ArrayList<GroupEventData>()

        when (event) {
            is GroupEvent.Blocked ->
                data.add(GroupEventData.MembershipLevelChange(event.id, GroupMembershipLevel.BLOCKED))

            is GroupEvent.Joined -> {
                data.add(GroupEventData.MembershipLevelChange(event.id, GroupMembershipLevel.JOINED))
                data.add(GroupEventData.MemberChange(event.id, event.members, emptySet()))
            }

            is GroupEvent.Parted ->
                data.add(GroupEventData.MembershipLevelChange(event.id, GroupMembershipLevel.PARTED))

            is GroupEvent.MembershipChanged ->
                data.add(GroupEventData.MemberChange(event.id, event.newMembers, event.partedMembers))
        }

        data.forEach {
            val logEvent = LogEvent.Group(LogTarget.Conversation(event.id), currentTimestamp(), it)
            eventLogService.addEvent(logEvent)
        }
    }

    override fun init() {
    }

    override fun shutdown() {
        subscription?.unsubscribe()
        subscription = null
    }
}