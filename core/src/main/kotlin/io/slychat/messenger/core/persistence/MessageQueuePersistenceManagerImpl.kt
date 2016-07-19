package io.slychat.messenger.core.persistence

import io.slychat.messenger.core.UserId
import io.slychat.messenger.core.persistence.sqlite.SQLitePersistenceManager
import nl.komponents.kovenant.Promise

class MessageQueuePersistenceManagerImpl(
    private val sqlitePersistenceManager: SQLitePersistenceManager
) : MessageQueuePersistenceManager {
    override fun add(queuedMessage: QueuedMessage): Promise<Unit, Exception> {
        throw NotImplementedError()
    }

    override fun add(queuedMessages: Iterable<QueuedMessage>): Promise<Unit, Exception> {
        throw NotImplementedError()
    }

    override fun remove(userId: UserId, messageId: String): Promise<Unit, Exception> {
        throw NotImplementedError()
    }

    override fun getUndelivered(): Promise<List<QueuedMessage>, Exception> {
        throw NotImplementedError()
    }
}