package io.slychat.messenger.core.persistence

import io.slychat.messenger.core.UserId
import nl.komponents.kovenant.Promise

interface MessageQueuePersistenceManager {
    fun add(entries: Collection<SenderMessageEntry>): Promise<List<QueuedMessage>, Exception>

    fun add(entry: SenderMessageEntry): Promise<QueuedMessage, Exception>

    fun remove(userId: UserId, messageId: String): Promise<Boolean, Exception>

    /** Returned list is ordered by timestamp. */
    fun getUndelivered(): Promise<List<QueuedMessage>, Exception>
}