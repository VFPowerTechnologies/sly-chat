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
    override fun add(entry: SenderMessageEntry): Promise<Unit, Exception> = sqlitePersistenceManager.runQuery { connection ->
        connection.withPrepared("INSERT INTO send_message_queue (contact_id, group_id, category, message_id, serialized) VALUES (?, ?, ?, ?, ?)") { stmt ->
            entryToRow(stmt, entry)
            stmt.step()
            Unit
        }
    }

    override fun add(entries: Collection<SenderMessageEntry>): Promise<Unit, Exception> = sqlitePersistenceManager.runQuery { connection ->
        connection.batchInsertWithinTransaction(
            "INSERT INTO send_message_queue (contact_id, group_id, category, message_id, serialized) VALUES (?, ?, ?, ?, ?)",
            entries,
            { stmt, v -> entryToRow(stmt, v) }
        )
    }

    fun get(userId: UserId, messageId: String): Promise<SenderMessageEntry?, Exception> = sqlitePersistenceManager.runQuery { connection ->
        connection.withPrepared("SELECT contact_id, group_id, category, message_id, serialized FROM send_message_queue WHERE contact_id=? AND message_id=?") { stmt ->
            stmt.bind(1, userId.long)
            stmt.bind(2, messageId)

            if (stmt.step()) {
                rowToEntry(stmt)
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

    private fun rowToEntry(stmt: SQLiteStatement): SenderMessageEntry {
        val groupId = if (stmt.columnNull(1)) null else GroupId(stmt.columnString(1))

        val metadata = MessageMetadata(
            UserId(stmt.columnLong(0)),
            groupId,
            MessageCategory.valueOf(stmt.columnString(2)),
            stmt.columnString(3)
        )

        val serialized = stmt.columnBlob(4)

        return SenderMessageEntry(metadata, serialized)
    }

    override fun remove(userId: UserId, messageId: String): Promise<Boolean, Exception> = sqlitePersistenceManager.runQuery { connection ->
        connection.withPrepared("DELETE FROM send_message_queue WHERE contact_id=? AND message_id=?") { stmt ->
            stmt.bind(1, userId.long)
            stmt.bind(2, messageId)

            stmt.step()
        }

        connection.changes > 0
    }

    override fun getUndelivered(): Promise<List<SenderMessageEntry>, Exception> = sqlitePersistenceManager.runQuery { connection ->
        connection.withPrepared("SELECT contact_id, group_id, category, message_id, serialized FROM send_message_queue ORDER BY id") { stmt ->
            stmt.map { rowToEntry(it) }
        }
    }
}