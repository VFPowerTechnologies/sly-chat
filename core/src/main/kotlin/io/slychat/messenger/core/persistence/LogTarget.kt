package io.slychat.messenger.core.persistence

import io.slychat.messenger.core.UserId

sealed class LogTarget {
    class Conversation(val id: ConversationId) : LogTarget() {
        constructor(userId: UserId) : this(userId.toConversationId())
        constructor(groupId: GroupId) : this(groupId.toConversationId())

        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other?.javaClass != javaClass) return false

            other as Conversation

            if (id != other.id) return false

            return true
        }

        override fun hashCode(): Int {
            return id.hashCode()
        }

        override fun toString(): String {
            return "Conversation(id=$id)"
        }
    }

    object System : LogTarget() {
        override fun toString(): String {
            return "System()"
        }
    }
}