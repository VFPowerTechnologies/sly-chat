package io.slychat.messenger.services.messaging

import io.slychat.messenger.core.UserId
import io.slychat.messenger.core.persistence.ConversationId
import io.slychat.messenger.core.persistence.GroupId
import io.slychat.messenger.core.persistence.MessageInfo
import io.slychat.messenger.core.persistence.toConversationId

sealed class ConversationMessage {
    abstract val info: MessageInfo

    abstract val conversationId: ConversationId

    class Group(val groupId: GroupId, val speaker: UserId?, override val info: MessageInfo) : ConversationMessage() {
        override val conversationId: ConversationId
            get() = groupId.toConversationId()

        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other?.javaClass != javaClass) return false

            other as Group

            if (groupId != other.groupId) return false
            if (speaker != other.speaker) return false
            if (info != other.info) return false

            return true
        }

        override fun hashCode(): Int {
            var result = groupId.hashCode()
            result = 31 * result + (speaker?.hashCode() ?: 0)
            result = 31 * result + info.hashCode()
            return result
        }

        override fun toString(): String {
            return "Group(groupId=$groupId, speaker=$speaker, info=$info)"
        }
    }

    class Single(val userId: UserId, override val info: MessageInfo) : ConversationMessage() {
        override val conversationId: ConversationId
            get() = userId.toConversationId()

        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other?.javaClass != javaClass) return false

            other as Single

            if (userId != other.userId) return false
            if (info != other.info) return false

            return true
        }

        override fun hashCode(): Int {
            var result = userId.hashCode()
            result = 31 * result + info.hashCode()
            return result
        }

        override fun toString(): String {
            return "Single(userId=$userId, info=$info)"
        }
    }
}