package io.slychat.messenger.core.persistence

class InvalidConversationException(val conversationId: ConversationId) : RuntimeException("No conversation exists for: $conversationId")