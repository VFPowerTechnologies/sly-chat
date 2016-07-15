package io.slychat.messenger.services

import io.slychat.messenger.core.UserId
import io.slychat.messenger.core.persistence.GroupId

sealed class GroupEvent {
    class Joined(val id: GroupId, val userId: UserId) : GroupEvent() {
        override fun toString(): String {
            return "Joined(id=$id, userId=$userId)"
        }
    }

    class Parted(val id: GroupId, val userId: UserId) : GroupEvent() {
        override fun toString(): String {
            return "Parted(id=$id, userId=$userId)"
        }
    }
}