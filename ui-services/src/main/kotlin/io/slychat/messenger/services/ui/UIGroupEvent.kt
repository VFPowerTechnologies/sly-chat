package io.slychat.messenger.services.ui

import io.slychat.messenger.core.UserId
import io.slychat.messenger.core.persistence.GroupId

enum class UIGroupEventType {
    NEW,
    JOINED,
    PARTED
}

sealed class UIGroupEvent {
    abstract val groupId: GroupId
    abstract val type: UIGroupEventType

    class NewGroup(override val groupId: GroupId, val members: Set<UserId>) : UIGroupEvent() {
        override val type: UIGroupEventType = UIGroupEventType.NEW
    }

    class Joined(override val groupId: GroupId, val newMembers: Set<UserId>) : UIGroupEvent() {
        override val type: UIGroupEventType = UIGroupEventType.JOINED
    }

    class Parted(override val groupId: GroupId, val member: UserId) : UIGroupEvent() {
        override val type: UIGroupEventType = UIGroupEventType.PARTED
    }
}