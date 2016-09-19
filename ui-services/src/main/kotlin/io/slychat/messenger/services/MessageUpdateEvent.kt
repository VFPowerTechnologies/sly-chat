package io.slychat.messenger.services

import io.slychat.messenger.core.persistence.ConversationId

sealed class MessageUpdateEvent {
    class Delivered(val conversationId: ConversationId, val messageId: String, val deliveredTimestamp: Long) : MessageUpdateEvent() {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other?.javaClass != javaClass) return false

            other as Delivered

            if (conversationId != other.conversationId) return false
            if (messageId != other.messageId) return false
            if (deliveredTimestamp != other.deliveredTimestamp) return false

            return true
        }

        override fun hashCode(): Int {
            var result = conversationId.hashCode()
            result = 31 * result + messageId.hashCode()
            result = 31 * result + deliveredTimestamp.hashCode()
            return result
        }

        override fun toString(): String {
            return "Delivered(conversationId=$conversationId, messageId='$messageId', deliveredTimestamp=$deliveredTimestamp)"
        }
    }

    class Expiring(val conversationId: ConversationId, val messageId: String, val ttl: Long, val expiresAt: Long) : MessageUpdateEvent() {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other?.javaClass != javaClass) return false

            other as Expiring

            if (conversationId != other.conversationId) return false
            if (messageId != other.messageId) return false
            if (ttl != other.ttl) return false
            if (expiresAt != other.expiresAt) return false

            return true
        }

        override fun hashCode(): Int {
            var result = conversationId.hashCode()
            result = 31 * result + messageId.hashCode()
            result = 31 * result + ttl.hashCode()
            result = 31 * result + expiresAt.hashCode()
            return result
        }

        override fun toString(): String {
            return "Expiring(conversationId=$conversationId, messageId='$messageId', ttl=$ttl, expiresAt=$expiresAt)"
        }
    }

    class Expired(val conversationId: ConversationId, val messageId: String, val fromSync: Boolean) : MessageUpdateEvent() {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other?.javaClass != javaClass) return false

            other as Expired

            if (conversationId != other.conversationId) return false
            if (messageId != other.messageId) return false
            if (fromSync != other.fromSync) return false

            return true
        }

        override fun hashCode(): Int {
            var result = conversationId.hashCode()
            result = 31 * result + messageId.hashCode()
            result = 31 * result + fromSync.hashCode()
            return result
        }

        override fun toString(): String {
            return "Expired(conversationId=$conversationId, messageId='$messageId', fromSync=$fromSync)"
        }
    }

    class Deleted(val conversationId: ConversationId, val messageIds: Collection<String>) : MessageUpdateEvent() {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other?.javaClass != javaClass) return false

            other as Deleted

            if (conversationId != other.conversationId) return false
            if (messageIds != other.messageIds) return false

            return true
        }

        override fun hashCode(): Int {
            var result = conversationId.hashCode()
            result = 31 * result + messageIds.hashCode()
            return result
        }

        override fun toString(): String {
            return "Deleted(conversationId=$conversationId, messageIds=$messageIds)"
        }
    }

    class DeletedAll(val conversationId: ConversationId) : MessageUpdateEvent() {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other?.javaClass != javaClass) return false

            other as DeletedAll

            if (conversationId != other.conversationId) return false

            return true
        }

        override fun hashCode(): Int {
            return conversationId.hashCode()
        }

        override fun toString(): String {
            return "DeletedAll(conversationId=$conversationId)"
        }
    }

    class Read(val conversationId: ConversationId, val messageIds: List<String>, val fromSync: Boolean) : MessageUpdateEvent() {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other?.javaClass != javaClass) return false

            other as Read

            if (conversationId != other.conversationId) return false
            if (messageIds != other.messageIds) return false
            if (fromSync != other.fromSync) return false

            return true
        }

        override fun hashCode(): Int {
            var result = conversationId.hashCode()
            result = 31 * result + messageIds.hashCode()
            result = 31 * result + fromSync.hashCode()
            return result
        }

        override fun toString(): String {
            return "Read(conversationId=$conversationId, messageIds=$messageIds, fromSync=$fromSync)"
        }
    }
}