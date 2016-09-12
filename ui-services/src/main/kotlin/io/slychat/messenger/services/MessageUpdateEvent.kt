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

    class Expired(val conversationId: ConversationId, val messageId: String) : MessageUpdateEvent() {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other?.javaClass != javaClass) return false

            other as Expired

            if (conversationId != other.conversationId) return false
            if (messageId != other.messageId) return false

            return true
        }

        override fun hashCode(): Int {
            var result = conversationId.hashCode()
            result = 31 * result + messageId.hashCode()
            return result
        }

        override fun toString(): String {
            return "Expired(conversationId=$conversationId, messageId='$messageId')"
        }
    }
}