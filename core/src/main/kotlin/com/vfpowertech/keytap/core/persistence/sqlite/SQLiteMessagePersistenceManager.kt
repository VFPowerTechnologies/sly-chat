package com.vfpowertech.keytap.core.persistence.sqlite

import com.almworks.sqlite4java.SQLiteConnection
import com.almworks.sqlite4java.SQLiteStatement
import com.vfpowertech.keytap.core.persistence.InvalidMessageException
import com.vfpowertech.keytap.core.persistence.MessageInfo
import com.vfpowertech.keytap.core.persistence.MessagePersistenceManager
import nl.komponents.kovenant.Promise
import org.joda.time.DateTime
import java.util.*

inline fun Boolean.toInt(): Int = if (this) 1 else 0

/** Depends on SQLiteContactsPersistenceManager for creating and deleting conversation tables. */
class SQLiteMessagePersistenceManager(
    private val sqlitePersistenceManager: SQLitePersistenceManager
) : MessagePersistenceManager {
    private fun getTablenameForContact(contact: String) =
        "`conv_${escapeBackticks(contact)}`"

    private fun getMessageId(): String =
        UUID.randomUUID().toString().replace("-", "")

    private fun getCurrentTimestamp(): Long = DateTime().millis

    //only for received messages
    private fun addMessagesNoTransaction(connection: SQLiteConnection, contact: String, messages: List<String>): List<MessageInfo> {
        return messages.map { message ->
            val messageInfo = newMessageInfo(false, message, 0)
            insertMessage(connection, contact, messageInfo)
            messageInfo
        }
    }

    private fun insertMessage(connection: SQLiteConnection, contact: String, messageInfo: MessageInfo) {
        val table = getTablenameForContact(contact)
        val sql = """
INSERT INTO $table
    (id, is_sent, timestamp, ttl, is_delivered, message, n)
VALUES
    (?, ?, ?, ?, ?, ?, (SELECT count(n)
                        FROM   $table
                        WHERE  timestamp = ?)+1)
"""

        val timestamp = getCurrentTimestamp()

        connection.prepare(sql).use { stmt ->
            messageInfoToRow(messageInfo, stmt)
            stmt.bind(7, timestamp)
            stmt.step()
        }
    }

    private fun newMessageInfo(isSent: Boolean, message: String, ttl: Long): MessageInfo {
        val id = getMessageId()
        val timestamp = getCurrentTimestamp()
        val isDelivered = !isSent

        return MessageInfo(id, message, timestamp, isSent, isDelivered, ttl)
    }

    private fun updateConversationInfo(connection: SQLiteConnection, contact: String, isSent: Boolean, lastMessage: String, unreadIncrement: Int) {
        val unreadCountFragment = if (!isSent) "unread_count=unread_count+$unreadIncrement," else ""

        connection.prepare("UPDATE conversation_info SET $unreadCountFragment last_message=? WHERE contact_email=?").use { stmt ->
            stmt.bind(1, lastMessage)
            stmt.bind(2, contact)
            stmt.step()
        }
    }

    //TODO retry if id taken
    override fun addMessage(contact: String, isSent: Boolean, message: String, ttl: Long): Promise<MessageInfo, Exception> = sqlitePersistenceManager.runQuery { connection ->
        val messageInfo = newMessageInfo(isSent, message, ttl)
        connection.withTransaction {
            insertMessage(connection, contact, messageInfo)
            updateConversationInfo(connection, contact, isSent, message, 1)
        }

        messageInfo
    }

    //TODO optimize this
    override fun addReceivedMessages(messages: Map<String, List<String>>): Promise<Map<String, List<MessageInfo>>, Exception> = sqlitePersistenceManager.runQuery { connection ->
        connection.withTransaction {
            messages.mapValues { e ->
                val contact = e.key
                val messages = e.value
                updateConversationInfo(connection, contact, false, messages.last(), messages.size)
                addMessagesNoTransaction(connection, contact, messages)
            }
        }
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
