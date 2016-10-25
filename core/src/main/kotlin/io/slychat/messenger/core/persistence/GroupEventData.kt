package io.slychat.messenger.core.persistence

import com.fasterxml.jackson.annotation.JsonProperty
import io.slychat.messenger.core.UserId

sealed class GroupEventData : EventData {
    class MembershipLevelChange(
        @JsonProperty("groupId")
        val groupId: GroupId,
        @JsonProperty("newLevel")
        val membershipLevel: GroupMembershipLevel
    ) : GroupEventData() {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other?.javaClass != javaClass) return false

            other as MembershipLevelChange

            if (groupId != other.groupId) return false
            if (membershipLevel != other.membershipLevel) return false

            return true
        }

        override fun hashCode(): Int {
            var result = groupId.hashCode()
            result = 31 * result + membershipLevel.hashCode()
            return result
        }

        override fun toString(): String {
            return "MembershipLevelChange(groupId=$groupId, newLevel=$membershipLevel)"
        }

        override fun toDisplayString(): String {
            return "Group $groupId changed to level $membershipLevel"
        }
    }

    class MemberChange(
        @JsonProperty("groupId")
        val groupId: GroupId,
        @JsonProperty("newMembers")
        val newMembers: Set<UserId>,
        @JsonProperty("partedMembers")
        val partedMembers: Set<UserId>
    ) : GroupEventData() {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other?.javaClass != javaClass) return false

            other as MemberChange

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
            return "MemberChange(groupId=$groupId, newMembers=$newMembers, partedMembers=$partedMembers)"
        }

        override fun toDisplayString(): String {
            return "Group $groupId membership change: +$newMembers ; -$partedMembers"
        }
    }
}