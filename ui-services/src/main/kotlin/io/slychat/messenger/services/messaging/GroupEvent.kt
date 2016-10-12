package io.slychat.messenger.services.messaging

import io.slychat.messenger.core.UserId
import io.slychat.messenger.core.persistence.GroupId

sealed class GroupEvent {
    class Joined(val id: GroupId, val name: String, val members: Set<UserId>, val fromSync: Boolean) : GroupEvent() {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other?.javaClass != javaClass) return false

            other as Joined

            if (id != other.id) return false
            if (name != other.name) return false
            if (members != other.members) return false
            if (fromSync != other.fromSync) return false

            return true
        }

        override fun hashCode(): Int {
            var result = id.hashCode()
            result = 31 * result + name.hashCode()
            result = 31 * result + members.hashCode()
            result = 31 * result + fromSync.hashCode()
            return result
        }

        override fun toString(): String {
            return "Joined(id=$id, name='$name', members=$members, fromSync=$fromSync)"
        }
    }

    class Blocked(val id: GroupId, val fromSync: Boolean) : GroupEvent() {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other?.javaClass != javaClass) return false

            other as Blocked

            if (id != other.id) return false

            return true
        }

        override fun hashCode(): Int {
            return id.hashCode()
        }

        override fun toString(): String {
            return "Blocked(groupId=$id)"
        }
    }

    class Parted(val id: GroupId, val fromSync: Boolean) : GroupEvent() {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other?.javaClass != javaClass) return false

            other as Parted

            if (id != other.id) return false

            return true
        }

        override fun hashCode(): Int {
            return id.hashCode()
        }

        override fun toString(): String {
            return "Parted(groupId=$id)"
        }
    }

    class MembershipChanged(val id: GroupId, val newMembers: Set<UserId>, val partedMembers: Set<UserId>, val fromSync: Boolean) : GroupEvent() {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other?.javaClass != javaClass) return false

            other as MembershipChanged

            if (id != other.id) return false
            if (newMembers != other.newMembers) return false
            if (partedMembers != other.partedMembers) return false

            return true
        }

        override fun hashCode(): Int {
            var result = id.hashCode()
            result = 31 * result + newMembers.hashCode()
            result = 31 * result + partedMembers.hashCode()
            return result
        }

        override fun toString(): String {
            return "MembershipChanged(groupId=$id, newMembers=$newMembers, partedMembers=$partedMembers)"
        }
    }
}