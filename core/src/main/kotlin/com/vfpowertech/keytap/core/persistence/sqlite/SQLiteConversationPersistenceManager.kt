package com.vfpowertech.keytap.core.persistence.sqlite

import com.almworks.sqlite4java.SQLiteConnection
import com.almworks.sqlite4java.SQLiteStatement
import com.vfpowertech.keytap.core.persistence.ConversationPersistenceManager
import com.vfpowertech.keytap.core.persistence.MessageInfo
import nl.komponents.kovenant.Promise
import org.joda.time.DateTime
import java.util.*

inline fun Boolean.toInt(): Int = if (this) 1 else 0

//TODO update conversation_info when adding new message

/** Depends on SQLiteContactsPersistenceManager for creating and deleting conversation tables. */
class SQLiteConversationPersistenceManager(
    private val sqlitePersistenceManager: SQLitePersistenceManager
) : ConversationPersistenceManager {
    private fun getTablenameForContact(contact: String) =
        "`conv_${escapeBackticks(contact)}`"

    private fun getMessageId(): String =
        UUID.randomUUID().toString().replace("-", "")

    private fun getCurrentTimestamp(): Long = DateTime().millis

    //TODO retry if id taken
    override fun addMessage(contact: String, isSent: Boolean, message: String, ttl: Long): Promise<MessageInfo, Exception> = sqlitePersistenceManager.runQuery { connection ->
        val table = getTablenameForContact(contact)
        val sql = """
INSERT INTO $table
    (id, is_sent, timestamp, ttl, is_delivered, message, n)
VALUES
    (?, ?, ?, ?, ?, ?, (SELECT count(n)
                        FROM   $table
                        WHERE  timestamp = ?))
"""

        val id = getMessageId()
        val timestamp = getCurrentTimestamp()
        val isDelivered = !isSent

        val messageInfo = MessageInfo(id, message, timestamp, isSent, isDelivered, ttl)

        connection.prepare(sql).use { stmt ->
            messageInfoToRow(messageInfo, stmt)
            stmt.bind(7, timestamp)
            stmt.step()
        }

        //TODO update conversation_info
        if (!isSent) {}

        messageInfo
    }

    override fun markMessageAsDelivered(contact: String, messageId: String): Promise<MessageInfo, Exception> = sqlitePersistenceManager.runQuery { connection ->
        val table = getTablenameForContact(contact)

        connection.prepare("UPDATE $table SET is_delivered=1, timestamp=? WHERE id=?").use { stmt ->
            stmt.bind(1, getCurrentTimestamp())
            stmt.bind(2, messageId)
            stmt.step()
        }

        if (connection.changes <= 0)
            throw InvalidMessageException(contact, messageId)

        connection.prepare("SELECT id, is_sent, timestamp, ttl, is_delivered, message FROM $table WHERE id=?").use { stmt ->
            stmt.bind(1, messageId)
            if (!stmt.step())
                throw InvalidMessageException(contact, messageId)
            rowToMessageInfo(stmt)
        }
    }

    private fun queryLastMessages(connection: SQLiteConnection, contact: String, startingAt: Int, count: Int): List<MessageInfo> {
        val sql = "SELECT id, is_sent, timestamp, ttl, is_delivered, message FROM ${getTablenameForContact(contact)} ORDER BY timestamp DESC LIMIT $count OFFSET $startingAt"
        return connection.prepare(sql).use { stmt ->
            stmt.map { rowToMessageInfo(it) }
        }
    }

    override fun getLastMessages(contact: String, startingAt: Int, count: Int): Promise<List<MessageInfo>, Exception> = sqlitePersistenceManager.runQuery { connection ->
        queryLastMessages(connection, contact, startingAt, count)
    }

    override fun getUndeliveredMessages(contact: String): Promise<List<MessageInfo>, Exception> = sqlitePersistenceManager.runQuery { connection ->
        val table = getTablenameForContact(contact)

        connection.prepare("SELECT id, is_sent, timestamp, ttl, is_delivered, message FROM $table WHERE is_delivered=0").use { stmt ->
            stmt.map { rowToMessageInfo(it) }
        }
    }

    private fun messageInfoToRow(messageInfo: MessageInfo, stmt: SQLiteStatement) {
        stmt.bind(1, messageInfo.id)
        stmt.bind(2, messageInfo.isSent.toInt())
        stmt.bind(3, messageInfo.timestamp)
        stmt.bind(4, messageInfo.ttl)
        stmt.bind(5, messageInfo.isDelivered.toInt())
        stmt.bind(6, messageInfo.message)
    }

    private fun rowToMessageInfo(stmt: SQLiteStatement): MessageInfo {
        val id = stmt.columnString(0)
        val isSent = stmt.columnInt(1) != 0
        val timestamp = stmt.columnLong(2)
        val ttl = stmt.columnLong(3)
        val isDelivered = stmt.columnInt(4) != 0
        val message = stmt.columnString(5)

        return MessageInfo(id, message, timestamp, isSent, isDelivered, ttl)
    }
}
