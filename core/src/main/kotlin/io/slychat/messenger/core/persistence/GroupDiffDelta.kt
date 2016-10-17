package io.slychat.messenger.core.persistence

import io.slychat.messenger.core.UserId

sealed class GroupDiffDelta {
    class Joined(val groupId: GroupId, val name: String, val members: Set<UserId>) : GroupDiffDelta() {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other?.javaClass != javaClass) return false

            other as Joined

            if (groupId != other.groupId) return false
            if (name != other.name) return false
            if (members != other.members) return false

            return true
        }

        override fun hashCode(): Int {
            var result = groupId.hashCode()
            result = 31 * result + name.hashCode()
            result = 31 * result + members.hashCode()
            return result
        }

        override fun toString(): String {
            return "Joined(groupId=$groupId, name='$name', members=$members)"
        }
    }

    class Blocked(val groupId: GroupId) : GroupDiffDelta() {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other?.javaClass != javaClass) return false

            other as Blocked

            if (groupId != other.groupId) return false

            return true
        }

        override fun hashCode(): Int {
            return groupId.hashCode()
        }

        override fun toString(): String {
            return "Blocked(groupId=$groupId)"
        }
    }

    class Parted(val groupId: GroupId) : GroupDiffDelta() {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other?.javaClass != javaClass) return false

            other as Parted

            if (groupId != other.groupId) return false

            return true
        }

        override fun hashCode(): Int {
            return groupId.hashCode()
        }

        override fun toString(): String {
            return "Parted(groupId=$groupId)"
        }
    }

    class MembershipChanged(val groupId: GroupId, val newMembers: Set<UserId>, val partedMembers: Set<UserId>) : GroupDiffDelta() {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other?.javaClass != javaClass) return false

            other as MembershipChanged

            if (groupId != other.groupId) return false
            if (newMembers != other.newMembers) return false
            if (partedMembers != other.partedMembers) return false

            return true
        }

        override fun hashCode(): Int {
            var result = groupId.hashCode()
            result = 31 * result + newMembers.hashCode()
            result = 31 * result + partedMembers.hashCode()
            return result
        }

        override fun toString(): String {
            return "MembershipChanged(groupId=$groupId, newMembers=$newMembers, partedMembers=$partedMembers)"
        }
    }
}