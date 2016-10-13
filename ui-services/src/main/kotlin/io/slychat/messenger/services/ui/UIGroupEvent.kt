package io.slychat.messenger.services.ui

import io.slychat.messenger.core.UserId
import io.slychat.messenger.core.persistence.GroupId

enum class UIGroupEventType {
    JOINED,
    PARTED,
    BLOCKED,
    MEMBERSHIP
}

sealed class UIGroupEvent {
    abstract val type: UIGroupEventType

    class Joined(val groupInfo: UIGroupInfo, val members: Set<UserId>) : UIGroupEvent() {
        override val type: UIGroupEventType = UIGroupEventType.JOINED
    }

    class Parted(val groupId: GroupId) : UIGroupEvent() {
        override val type: UIGroupEventType = UIGroupEventType.PARTED
    }

    class Blocked(val groupId: GroupId) : UIGroupEvent() {
        override val type: UIGroupEventType = UIGroupEventType.BLOCKED
    }

    class MembershipChanged(val groupId: GroupId, val newMembers: Set<UserId>, val partedMembers: Set<UserId>) : UIGroupEvent() {
        override val type: UIGroupEventType = UIGroupEventType.MEMBERSHIP
    }
}