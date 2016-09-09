package io.slychat.messenger.services.messaging

import io.slychat.messenger.core.persistence.ConversationId

class ExpiringMessages {
    data class ExpiringEntry(val conversationId: ConversationId, val expireAt: Long)

    fun add() {}
    //remove if exists, else do nothing
    //fun remove(conversationId: ConversationId, messageId: Long) {}
    //fun removeExpiredAt(currentTime: Long): List<>

    //0 if empty
    //absolute system time of next expiration (basicly the first item's expireAt)
    fun nextExpirationAt(): Long { return 0 }
}