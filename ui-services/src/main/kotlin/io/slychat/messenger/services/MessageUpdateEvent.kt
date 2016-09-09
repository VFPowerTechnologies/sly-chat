package io.slychat.messenger.services

import io.slychat.messenger.core.persistence.ConversationId

sealed class MessageUpdateEvent {
    class Delivered(val conversationId: ConversationId, val messageId: String, val deliveredTimestamp: Long) : MessageUpdateEvent()
    class Expiring(val conversationId: ConversationId, val messageId: String, val ttl: Long, val expiresAt: Long) : MessageUpdateEvent()
    class Expired(val conversationId: ConversationId, val messageId: String) : MessageUpdateEvent()
}