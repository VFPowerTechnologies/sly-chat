package io.slychat.messenger.core.persistence

import io.slychat.messenger.core.UserId
import nl.komponents.kovenant.Promise

interface MessageQueuePersistenceManager {
    fun add(entries: Collection<SenderMessageEntry>): Promise<Unit, Exception>

    fun add(entry: SenderMessageEntry): Promise<Unit, Exception>

    fun remove(userId: UserId, messageId: String): Promise<Boolean, Exception>

    fun removeAll(conversationId: ConversationId, messageIds: Collection<String>): Promise<Boolean, Exception>

    fun removeAllForConversation(conversationId: ConversationId): Promise<Boolean, Exception>

    /** Returned list is ordered by original insertion order. */
    fun getUndelivered(): Promise<List<SenderMessageEntry>, Exception>
}