package com.vfpowertech.keytap.core.persistence.sqlite

import com.almworks.sqlite4java.SQLiteConnection
import com.almworks.sqlite4java.SQLiteException
import com.almworks.sqlite4java.SQLiteStatement
import com.vfpowertech.keytap.core.persistence.ConversationInfo
import com.vfpowertech.keytap.core.persistence.ConversationPersistenceManager
import com.vfpowertech.keytap.core.persistence.InvalidConversationException
import com.vfpowertech.keytap.core.persistence.MessageInfo
import nl.komponents.kovenant.Promise
import org.joda.time.DateTime
import java.util.*

inline fun Boolean.toInt(): Int = if (this) 1 else 0

/** Depends on SQLiteContactsPersistenceManager for creating and deleting conversation tables. */
class SQLiteConversationPersistenceManager(
    private val sqlitePersistenceManager: SQLitePersistenceManager
) : ConversationPersistenceManager {
    private fun getTablenameForContact(contact: String) =
        "`conv_${escapeBackticks(contact)}`"

    private fun getMessageId(): String =
        UUID.randomUUID().toString().replace("-", "")

    private fun getCurrentTimestamp(): Long = DateTime().millis

    override fun createNewConversation(contact: String): Promise<Unit, Exception> = sqlitePersistenceManager.runQuery { connection ->
        ConversationTable.create(connection, contact)
    }

    override fun deleteConversation(contact: String): Promise<Unit, Exception> = sqlitePersistenceManager.runQuery { connection ->
        ConversationTable.delete(connection, contact)
    }

    //TODO retry if id taken
    override fun addMessage(contact: String, isSent: Boolean, message: String, ttl: Long): Promise<MessageInfo, Exception> = sqlitePersistenceManager.runQuery { connection ->
        val table = getTablenameForContact(contact)
        val sql = """
INSERT INTO $table
    (id, is_sent, timestamp, ttl, is_delivered, is_read, message, n)
VALUES
    (?, ?, ?, ?, ?, ?, ?, (SELECT count(n)
                           FROM   $table
                           WHERE  timestamp = ?))
"""

        val id = getMessageId()
        val timestamp = getCurrentTimestamp()
        val isDelivered = !isSent
        val isRead = isSent

        val messageInfo = MessageInfo(id, message, timestamp, isSent, isDelivered, isRead, ttl)

        connection.prepare(sql).use { stmt ->
            messageInfoToRow(messageInfo, stmt)
            stmt.bind(8, timestamp)
            stmt.step()
        }

        messageInfo
    }

    override fun markMessageAsDelivered(contact: String, messageId: String): Promise<MessageInfo, Exception> = sqlitePersistenceManager.runQuery { connection ->
        val table = getTablenameForContact(contact)

        connection.prepare("UPDATE $table SET is_delivered=1, timestamp=? WHERE id=?").use { stmt ->
            stmt.bind(1, getCurrentTimestamp())
            stmt.bind(2, messageId)
            stmt.step()
        }

        connection.prepare("SELECT id, is_sent, timestamp, ttl, is_delivered, is_read, message FROM $table WHERE id=?").use { stmt ->
            stmt.bind(1, messageId)
            stmt.step()
            rowToMessageInfo(stmt)
        }
    }

    private fun queryConversationInfo(connection: SQLiteConnection, contact: String): ConversationInfo {
        val table = getTablenameForContact(contact)

        val unreadCount = connection.prepare("SELECT count(is_read) FROM $table WHERE is_read=0").use { stmt ->
            stmt.step()
            stmt.columnInt(0)
        }

        val messages = queryLastMessages(connection, contact, 0, 1)
        val lastMessage = if (messages.isEmpty())
            null
        else
            messages[0].message

        return ConversationInfo(contact, unreadCount, lastMessage)
    }

    override fun getConversationInfo(contact: String): Promise<ConversationInfo, Exception> = sqlitePersistenceManager.runQuery { connection ->
        try {
            queryConversationInfo(connection, contact)
        }
        catch (e: SQLiteException) {
            if (isInvalidTableException(e))
                throw InvalidConversationException(contact)
            else
                throw e
        }
    }

    override fun getAllConversations(): Promise<List<ConversationInfo>, Exception> = sqlitePersistenceManager.runQuery { connection ->
        val convos = connection.prepare("SELECT name FROM sqlite_master WHERE type='table' AND name LIKE 'conv_%'").use { stmt ->
            stmt.map { it.columnString(0).substring(5) }
        }

        convos.map { queryConversationInfo(connection, it) }
    }

    override fun markConversationAsRead(contact: String): Promise<Unit, Exception> = sqlitePersistenceManager.runQuery { connection ->
        try {
            connection.exec("UPDATE ${getTablenameForContact(contact)} SET is_read=1 WHERE is_read=0")
        }
        catch (e: SQLiteException) {
            if (isInvalidTableException(e))
                throw InvalidConversationException(contact)
            else
                throw e
        }

        Unit
    }

    private fun queryLastMessages(connection: SQLiteConnection, contact: String, startingAt: Int, count: Int): List<MessageInfo> {
        val sql = "SELECT id, is_sent, timestamp, ttl, is_delivered, is_read, message FROM ${getTablenameForContact(contact)} ORDER BY timestamp DESC LIMIT $count OFFSET $startingAt"
        return connection.prepare(sql).use { stmt ->
            stmt.map { rowToMessageInfo(it) }
        }
    }

    override fun getLastMessages(contact: String, startingAt: Int, count: Int): Promise<List<MessageInfo>, Exception> = sqlitePersistenceManager.runQuery { connection ->
        queryLastMessages(connection, contact, startingAt, count)
    }

    override fun getUndeliveredMessages(contact: String): Promise<List<MessageInfo>, Exception> = sqlitePersistenceManager.runQuery { connection ->
        val table = getTablenameForContact(contact)

        connection.prepare("SELECT id, is_sent, timestamp, ttl, is_delivered, is_read, message FROM $table WHERE is_delivered=0").use { stmt ->
            stmt.map { rowToMessageInfo(it) }
        }
    }

    private fun messageInfoToRow(messageInfo: MessageInfo, stmt: SQLiteStatement) {
        stmt.bind(1, messageInfo.id)
        stmt.bind(2, messageInfo.isSent.toInt())
        stmt.bind(3, messageInfo.timestamp)
        stmt.bind(4, messageInfo.ttl)
        stmt.bind(5, messageInfo.isDelivered.toInt())
        stmt.bind(6, messageInfo.isRead.toInt())
        stmt.bind(7, messageInfo.message)
    }

    private fun rowToMessageInfo(stmt: SQLiteStatement): MessageInfo {
        val id = stmt.columnString(0)
        val isSent = stmt.columnInt(1) != 0
        val timestamp = stmt.columnLong(2)
        val ttl = stmt.columnLong(3)
        val isDelivered = stmt.columnInt(4) != 0
        val isRead = stmt.columnInt(5) != 0
        val message = stmt.columnString(6)

        return MessageInfo(id, message, timestamp, isSent, isDelivered, isRead, ttl)
    }
}
