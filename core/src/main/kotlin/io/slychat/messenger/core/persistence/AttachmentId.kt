package io.slychat.messenger.core.persistence

data class AttachmentId(val conversationId: ConversationId, val messageId: String, val n: Int)