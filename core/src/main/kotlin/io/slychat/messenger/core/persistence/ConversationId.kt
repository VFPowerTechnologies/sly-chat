package io.slychat.messenger.core.persistence

import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.annotation.JsonSubTypes
import com.fasterxml.jackson.annotation.JsonTypeInfo
import io.slychat.messenger.core.UserId

@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.PROPERTY, property = "t")
@JsonSubTypes(
    JsonSubTypes.Type(ConversationId.User::class, name = "u"),
    JsonSubTypes.Type(ConversationId.Group::class, name = "g")
)
sealed class ConversationId {
    class User(@JsonProperty("id") val id: UserId) : ConversationId() {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other?.javaClass != javaClass) return false

            other as User

            if (id != other.id) return false

            return true
        }

        override fun hashCode(): Int {
            return id.hashCode()
        }

        override fun toString(): String {
            return "User($id)"
        }
    }

    class Group(@JsonProperty("id") val id: GroupId) : ConversationId() {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other?.javaClass != javaClass) return false

            other as Group

            if (id != other.id) return false

            return true
        }

        override fun hashCode(): Int {
            return id.hashCode()
        }

        override fun toString(): String {
            return "Group($id)"
        }
    }

    companion object {
        operator fun invoke(userId: UserId): ConversationId.User = ConversationId.User(userId)
        operator fun invoke(groupId: GroupId): ConversationId.Group = ConversationId.Group(groupId)
    }
}

fun UserId.toConversationId(): ConversationId.User = ConversationId.User(this)

fun GroupId.toConversationId(): ConversationId.Group = ConversationId.Group(this)
