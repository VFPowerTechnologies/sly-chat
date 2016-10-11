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
    abstract val groupId: GroupId
    abstract val type: UIGroupEventType

    class Joined(override val groupId: GroupId, val members: Set<UserId>) : UIGroupEvent() {
        override val type: UIGroupEventType = UIGroupEventType.JOINED
    }

    class Parted(override val groupId: GroupId) : UIGroupEvent() {
        override val type: UIGroupEventType = UIGroupEventType.PARTED
    }

    class Blocked(override val groupId: GroupId) : UIGroupEvent() {
        override val type: UIGroupEventType = UIGroupEventType.BLOCKED
    }

    class MembershipChanged(override val groupId: GroupId, val newMembers: Set<UserId>, val partedMembers: Set<UserId>) : UIGroupEvent() {
        override val type: UIGroupEventType = UIGroupEventType.MEMBERSHIP
    }
}