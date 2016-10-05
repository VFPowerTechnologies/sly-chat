package io.slychat.messenger.core.persistence

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.annotation.JsonSubTypes
import com.fasterxml.jackson.annotation.JsonTypeInfo
import io.slychat.messenger.core.UserId

/**
 * Represents an update that must be pushed to the remote address book.
 *
 * Created when local contacts or groups are modified.
 */
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.PROPERTY, property = "t")
@JsonSubTypes(
    JsonSubTypes.Type(AddressBookUpdate.Contact::class, name = "c"),
    JsonSubTypes.Type(AddressBookUpdate.Group::class, name = "g")
)
sealed class AddressBookUpdate {
    @JsonIgnoreProperties(ignoreUnknown = true)
    class Group(
        @JsonProperty("groupId")
        val groupId: GroupId,
        @JsonProperty("name")
        val name: String,
        @JsonProperty("members")
        val members: Set<UserId>,
        @JsonProperty("membershipLevel")
        val membershipLevel: GroupMembershipLevel
    ) : AddressBookUpdate() {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other?.javaClass != javaClass) return false

            other as Group

            if (groupId != other.groupId) return false
            if (name != other.name) return false
            if (members != other.members) return false
            if (membershipLevel != other.membershipLevel) return false

            return true
        }

        override fun hashCode(): Int {
            var result = groupId.hashCode()
            result = 31 * result + name.hashCode()
            result = 31 * result + members.hashCode()
            result = 31 * result + membershipLevel.hashCode()
            return result
        }

        override fun toString(): String {
            return "Group(groupId=$groupId, name='$name', members=$members, membershipLevel=$membershipLevel)"
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    class Contact(
        @JsonProperty("userId")
        val userId: UserId,
        @JsonProperty("allowedMessageLevel")
        val allowedMessageLevel: AllowedMessageLevel
    ) : AddressBookUpdate() {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other?.javaClass != javaClass) return false

            other as Contact

            if (userId != other.userId) return false
            if (allowedMessageLevel != other.allowedMessageLevel) return false

            return true
        }

        override fun hashCode(): Int {
            var result = userId.hashCode()
            result = 31 * result + allowedMessageLevel.hashCode()
            return result
        }

        override fun toString(): String {
            return "Contact(userId=$userId, allowedMessageLevel=$allowedMessageLevel)"
        }
    }
}

