package io.slychat.messenger.core.persistence

import com.almworks.sqlite4java.SQLiteStatement
import io.slychat.messenger.core.UserId
import io.slychat.messenger.core.persistence.sqlite.SQLitePersistenceManager
import io.slychat.messenger.core.persistence.sqlite.batchInsertWithinTransaction
import io.slychat.messenger.core.persistence.sqlite.map
import io.slychat.messenger.core.persistence.sqlite.withPrepared
import nl.komponents.kovenant.Promise

class SQLiteMessageQueuePersistenceManager(
    private val sqlitePersistenceManager: SQLitePersistenceManager
) : MessageQueuePersistenceManager {
    override fun add(queuedMessage: QueuedMessage): Promise<Unit, Exception> = sqlitePersistenceManager.runQuery { connection ->
        connection.withPrepared("INSERT INTO send_message_queue (user_id, group_id, category, message_id, timestamp, serialized) VALUES (?, ?, ?, ?, ?, ?)") { stmt ->
            queuedMessageToRow(stmt, queuedMessage)
            stmt.step()
            Unit
        }
    }

    override fun add(queuedMessages: Collection<QueuedMessage>): Promise<Unit, Exception> = sqlitePersistenceManager.runQuery { connection ->
        connection.batchInsertWithinTransaction(
            "INSERT INTO send_message_queue (user_id, group_id, category, message_id, timestamp, serialized) VALUES (?, ?, ?, ?, ?, ?)",
            queuedMessages,
            { stmt, v -> queuedMessageToRow(stmt, v) }
        )
    }

    fun get(userId: UserId, messageId: String): Promise<QueuedMessage?, Exception> = sqlitePersistenceManager.runQuery { connection ->
        connection.withPrepared("SELECT user_id, group_id, category, message_id, timestamp, serialized FROM send_message_queue WHERE user_id=? AND message_id=?") { stmt ->
            stmt.bind(1, userId.long)
            stmt.bind(2, messageId)

            if (stmt.step()) {
                rowToQueuedMessage(stmt)
            }
            else
                null
        }
    }

    private fun queuedMessageToRow(stmt: SQLiteStatement, qm: QueuedMessage) {
        stmt.bind(1, qm.metadata.userId.long)
        stmt.bind(2, qm.metadata.groupId?.let { it.string })
        stmt.bind(3, qm.metadata.category.toString())
        stmt.bind(4, qm.metadata.messageId)
        stmt.bind(5, qm.timestamp)
        stmt.bind(6, qm.serialized)
    }

    private fun rowToQueuedMessage(stmt: SQLiteStatement): QueuedMessage {
        val groupId = if (stmt.columnNull(1)) null else GroupId(stmt.columnString(1))

        val metadata = MessageMetadata(
            UserId(stmt.columnLong(0)),
            groupId,
            MessageCategory.valueOf(stmt.columnString(2)),
            stmt.columnString(3)
        )

        val timestamp = stmt.columnLong(4)
        val serialized = stmt.columnBlob(5)

        return QueuedMessage(metadata, timestamp, serialized)
    }

    override fun remove(userId: UserId, messageId: String): Promise<Boolean, Exception> = sqlitePersistenceManager.runQuery { connection ->
        connection.withPrepared("DELETE FROM send_message_queue WHERE user_id=? AND message_id=?") { stmt ->
            stmt.bind(1, userId.long)
            stmt.bind(2, messageId)

            stmt.step()
        }

        connection.changes > 0
    }

    override fun getUndelivered(): Promise<List<QueuedMessage>, Exception> = sqlitePersistenceManager.runQuery { connection ->
        connection.withPrepared("SELECT user_id, group_id, category, message_id, timestamp, serialized FROM send_message_queue ORDER BY timestamp") { stmt ->
            stmt.map { rowToQueuedMessage(it) }
        }
    }
}