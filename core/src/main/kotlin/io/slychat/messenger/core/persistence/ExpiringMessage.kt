package io.slychat.messenger.core.persistence

data class ExpiringMessage(val conversationId: ConversationId, val messageId: String, val expiresAt: Long)