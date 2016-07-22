package io.slychat.messenger.services.messaging

import io.slychat.messenger.core.UserId
import io.slychat.messenger.core.persistence.GroupId

sealed class GroupEvent {
    class NewGroup(val id: GroupId, val members: Set<UserId>) : GroupEvent() {
        override fun toString(): String{
            return "NewGroup(id=$id, members=$members)"
        }
    }

    class Joined(val id: GroupId, val newMembers: Set<UserId>) : GroupEvent() {
        override fun toString(): String {
            return "Joined(id=$id, newMembers=$newMembers)"
        }
    }

    class Parted(val id: GroupId, val member: UserId) : GroupEvent() {
        override fun toString(): String {
            return "Parted(id=$id, member=$member)"
        }
    }
}