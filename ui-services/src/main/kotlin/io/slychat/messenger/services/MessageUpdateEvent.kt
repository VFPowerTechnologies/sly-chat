package io.slychat.messenger.services

import io.slychat.messenger.core.persistence.ConversationId

sealed class MessageUpdateEvent {
    class Delivered(val conversationId: ConversationId, val messageId: String, val deliveredTimestamp: Long) : MessageUpdateEvent()
}