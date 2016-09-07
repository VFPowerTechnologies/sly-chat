package io.slychat.messenger.core.persistence

import io.slychat.messenger.core.UserId

sealed class ConversationId {
    class User(val id: UserId) : ConversationId() {
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

    class Group(val id: GroupId) : ConversationId() {
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

fun UserId.toConversationId(): ConversationId.User = ConversationId(this)

fun GroupId.toConversationId(): ConversationId.Group = ConversationId(this)
