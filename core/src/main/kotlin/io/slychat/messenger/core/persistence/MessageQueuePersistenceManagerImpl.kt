package io.slychat.messenger.core.persistence

import com.almworks.sqlite4java.SQLiteStatement
import io.slychat.messenger.core.UserId
import io.slychat.messenger.core.persistence.sqlite.SQLitePersistenceManager
import io.slychat.messenger.core.persistence.sqlite.map
import io.slychat.messenger.core.persistence.sqlite.withPrepared
import io.slychat.messenger.core.persistence.sqlite.withTransaction
import nl.komponents.kovenant.Promise
import java.util.*

class SQLiteMessageQueuePersistenceManager(
    private val sqlitePersistenceManager: SQLitePersistenceManager
) : MessageQueuePersistenceManager {
    override fun add(entry: SenderMessageEntry): Promise<QueuedMessage, Exception> = sqlitePersistenceManager.runQuery { connection ->
        connection.withPrepared("INSERT INTO send_message_queue (contact_id, group_id, category, message_id, serialized) VALUES (?, ?, ?, ?, ?)") { stmt ->
            entryToRow(stmt, entry)
            stmt.step()
            Unit
        }

        QueuedMessage(
            entry.metadata,
            connection.lastInsertId,
            entry.message
        )
    }

    override fun add(entries: Collection<SenderMessageEntry>): Promise<List<QueuedMessage>, Exception> = sqlitePersistenceManager.runQuery { connection ->
        val r = ArrayList<QueuedMessage>()

        connection.withTransaction {
            val sql = "INSERT INTO send_message_queue (contact_id, group_id, category, message_id, serialized) VALUES (?, ?, ?, ?, ?)"
            connection.withPrepared(sql) { stmt ->
                entries.forEach {
                    entryToRow(stmt, it)
                    stmt.step()
                    stmt.reset(true)
                    r.add(
                        QueuedMessage(it.metadata, connection.lastInsertId, it.message)
                    )
                }
            }
        }

        r
    }

    fun get(userId: UserId, messageId: String): Promise<QueuedMessage?, Exception> = sqlitePersistenceManager.runQuery { connection ->
        connection.withPrepared("SELECT contact_id, group_id, category, message_id, id, serialized FROM send_message_queue WHERE contact_id=? AND message_id=?") { stmt ->
            stmt.bind(1, userId.long)
            stmt.bind(2, messageId)

            if (stmt.step()) {
                rowToQueuedMessage(stmt)
            }
            else
                null
        }
    }

    private fun entryToRow(stmt: SQLiteStatement, entry: SenderMessageEntry) {
        stmt.bind(1, entry.metadata.userId.long)
        stmt.bind(2, entry.metadata.groupId?.let { it.string })
        stmt.bind(3, entry.metadata.category.toString())
        stmt.bind(4, entry.metadata.messageId)
        stmt.bind(5, entry.message)
    }

    private fun rowToQueuedMessage(stmt: SQLiteStatement): QueuedMessage {
        val groupId = if (stmt.columnNull(1)) null else GroupId(stmt.columnString(1))

        val metadata = MessageMetadata(
            UserId(stmt.columnLong(0)),
            groupId,
            MessageCategory.valueOf(stmt.columnString(2)),
            stmt.columnString(3)
        )

        val id = stmt.columnLong(4)
        val serialized = stmt.columnBlob(5)

        return QueuedMessage(metadata, id, serialized)
    }

    override fun remove(userId: UserId, messageId: String): Promise<Boolean, Exception> = sqlitePersistenceManager.runQuery { connection ->
        connection.withPrepared("DELETE FROM send_message_queue WHERE contact_id=? AND message_id=?") { stmt ->
            stmt.bind(1, userId.long)
            stmt.bind(2, messageId)

            stmt.step()
        }

        connection.changes > 0
    }

    override fun getUndelivered(): Promise<List<QueuedMessage>, Exception> = sqlitePersistenceManager.runQuery { connection ->
        connection.withPrepared("SELECT contact_id, group_id, category, message_id, id, serialized FROM send_message_queue ORDER BY id") { stmt ->
            stmt.map { rowToQueuedMessage(it) }
        }
    }
}