package io.slychat.messenger.core.persistence

class InvalidConversationMessageException(val conversationId: ConversationId, val messageId: String) : RuntimeException("Invalid message id ($messageId) for conversation $conversationId")
