package com.vfpowertech.keytap.core.persistence.sqlite

import com.almworks.sqlite4java.SQLiteConnection
import com.almworks.sqlite4java.SQLiteStatement
import com.vfpowertech.keytap.core.UserId
import com.vfpowertech.keytap.core.persistence.InvalidMessageException
import com.vfpowertech.keytap.core.persistence.MessageInfo
import com.vfpowertech.keytap.core.persistence.MessagePersistenceManager
import nl.komponents.kovenant.Promise
import org.joda.time.DateTime
import java.util.*

fun Boolean.toInt(): Int = if (this) 1 else 0

/** Depends on SQLiteContactsPersistenceManager for creating and deleting conversation tables. */
class SQLiteMessagePersistenceManager(
    private val sqlitePersistenceManager: SQLitePersistenceManager
) : MessagePersistenceManager {
    private fun getMessageId(): String =
        UUID.randomUUID().toString().replace("-", "")

    private fun getCurrentTimestamp(): Long = DateTime().millis

    //only for received messages
    private fun addMessagesNoTransaction(connection: SQLiteConnection, userId: UserId, messages: List<String>): List<MessageInfo> {
        return messages.map { message ->
            val messageInfo = newMessageInfo(false, message, 0)
            insertMessage(connection, userId, messageInfo)
            messageInfo
        }
    }

    private fun insertMessage(connection: SQLiteConnection, userId: UserId, messageInfo: MessageInfo) {
        val table = ConversationTable.getTablenameForContact(userId)
        val sql = """
INSERT INTO $table
    (id, is_sent, timestamp, ttl, is_delivered, message, n)
VALUES
    (?, ?, ?, ?, ?, ?, (SELECT count(n)
                        FROM   $table
                        WHERE  timestamp = ?)+1)
"""

        connection.prepare(sql).use { stmt ->
            messageInfoToRow(messageInfo, stmt)
            stmt.bind(7, messageInfo.timestamp)
            stmt.step()
        }
    }

    private fun newMessageInfo(isSent: Boolean, message: String, ttl: Long): MessageInfo {
        val id = getMessageId()
        val timestamp = getCurrentTimestamp()
        val isDelivered = !isSent

        return MessageInfo(id, message, timestamp, isSent, isDelivered, ttl)
    }

    private fun updateConversationInfo(connection: SQLiteConnection, userId: UserId, isSent: Boolean, lastMessage: String, lastTimestamp: Long, unreadIncrement: Int) {
        val unreadCountFragment = if (!isSent) "unread_count=unread_count+$unreadIncrement," else ""

        connection.prepare("UPDATE conversation_info SET $unreadCountFragment last_message=?, last_timestamp=? WHERE contact_id=?").use { stmt ->
            stmt.bind(1, lastMessage)
            stmt.bind(2, lastTimestamp)
            stmt.bind(3, userId.long)
            stmt.step()
        }
    }

    private fun addMessageReal(connection: SQLiteConnection, userId: UserId, messageInfo: MessageInfo): MessageInfo {
        connection.withTransaction {
            insertMessage(connection, userId, messageInfo)
            updateConversationInfo(connection, userId, messageInfo.isSent, messageInfo.message, messageInfo.timestamp, 1)
        }

        return messageInfo
    }

    //TODO retry if id taken
    override fun addMessage(userId: UserId, isSent: Boolean, message: String, ttl: Long): Promise<MessageInfo, Exception> = sqlitePersistenceManager.runQuery { connection ->
        val messageInfo = newMessageInfo(isSent, message, ttl)
        addMessageReal(connection, userId, messageInfo)
    }

    override fun addSelfMessage(userId: UserId, message: String): Promise<MessageInfo, Exception> = sqlitePersistenceManager.runQuery { connection ->
        val messageInfo = MessageInfo(getMessageId(), message, getCurrentTimestamp(), true, true, 0)

        addMessageReal(connection, userId, messageInfo)
    }

    //TODO optimize this
    override fun addReceivedMessages(messages: Map<UserId, List<String>>): Promise<Map<UserId, List<MessageInfo>>, Exception> = sqlitePersistenceManager.runQuery { connection ->
        connection.withTransaction {
            messages.mapValues { e ->
                val userId = e.key
                val messages = e.value
                val info = addMessagesNoTransaction(connection, userId, messages)
                updateConversationInfo(connection, userId, false, messages.last(), info.last().timestamp, messages.size)
                info
            }
        }
    }

    override fun markMessageAsDelivered(userId: UserId, messageId: String): Promise<MessageInfo, Exception> = sqlitePersistenceManager.runQuery { connection ->
        val table = ConversationTable.getTablenameForContact(userId)

        connection.prepare("UPDATE $table SET is_delivered=1, timestamp=? WHERE id=?").use { stmt ->
            stmt.bind(1, getCurrentTimestamp())
            stmt.bind(2, messageId)
            stmt.step()
        }

        if (connection.changes <= 0)
            throw InvalidMessageException(userId, messageId)

        connection.prepare("SELECT id, is_sent, timestamp, ttl, is_delivered, message FROM $table WHERE id=?").use { stmt ->
            stmt.bind(1, messageId)
            if (!stmt.step())
                throw InvalidMessageException(userId, messageId)
            rowToMessageInfo(stmt)
        }
    }

    private fun queryLastMessages(connection: SQLiteConnection, userId: UserId, startingAt: Int, count: Int): List<MessageInfo> {
        val sql = "SELECT id, is_sent, timestamp, ttl, is_delivered, message FROM ${ConversationTable.getTablenameForContact(userId)} ORDER BY timestamp DESC, n DESC LIMIT $count OFFSET $startingAt"
        return connection.prepare(sql).use { stmt ->
            stmt.map { rowToMessageInfo(it) }
        }
    }

    override fun getLastMessages(userId: UserId, startingAt: Int, count: Int): Promise<List<MessageInfo>, Exception> = sqlitePersistenceManager.runQuery { connection ->
        queryLastMessages(connection, userId, startingAt, count)
    }

    override fun getUndeliveredMessages(): Promise<Map<UserId, List<MessageInfo>>, Exception> = sqlitePersistenceManager.runQuery { connection ->
        val contactIds = connection.prepare("SELECT contact_id FROM conversation_info").use { stmt ->
            stmt.map { UserId(it.columnLong(0)) }
        }

        val r = HashMap<UserId, List<MessageInfo>>()

        for (userId in contactIds) {
            val table = ConversationTable.getTablenameForContact(userId)

            val messages = connection.prepare("SELECT id, is_sent, timestamp, ttl, is_delivered, message FROM $table WHERE is_delivered=0 ORDER BY timestamp, n").use { stmt ->
                stmt.map { rowToMessageInfo(it) }
            }

            if (messages.isNotEmpty())
                r[userId] = messages
        }

        r
    }

    private fun getLastConvoMessage(connection: SQLiteConnection, userId: UserId): MessageInfo? {
        val table = ConversationTable.getTablenameForContact(userId)

        return connection.prepare("SELECT id, is_sent, timestamp, ttl, is_delivered, message FROM $table ORDER BY timestamp DESC, n DESC LIMIT 1").use { stmt ->
            if (!stmt.step())
                null
            else
                rowToMessageInfo(stmt)
        }
    }

    override fun deleteMessages(userId: UserId, messageIds: List<String>): Promise<Unit, Exception> = sqlitePersistenceManager.runQuery { connection ->
        if (!messageIds.isEmpty()) {
            val table = ConversationTable.getTablenameForContact(userId)

            connection.prepare("DELETE FROM $table WHERE id IN (${getPlaceholders(messageIds.size)})").use { stmt ->
                for (i in 1..messageIds.size)
                    stmt.bind(i, messageIds[i-1])

                stmt.step()
            }

            val lastMessage = getLastConvoMessage(connection, userId)
            if (lastMessage == null) {
                resetConversationInfo(connection, userId)
            }
            else {
                //regarding the unread count
                //right now we can't do squat about this... we don't actually keep track of which individual messages are unread
                //although, if someone deletes an individual message, they're in the contact chat's page, which means the unread count
                //would have been set to zero anyways
                updateConversationInfo(connection, userId, lastMessage.isSent, lastMessage.message, lastMessage.timestamp, 0)
            }
        }

        Unit
    }

    private fun resetConversationInfo(connection: SQLiteConnection, userId: UserId) {
        connection.prepare("UPDATE conversation_info set unread_count=0, last_message=null, last_timestamp=null where contact_id=?").use { stmt ->
            stmt.bind(1, userId.long)
            stmt.step()
        }
    }

    override fun deleteAllMessages(userId: UserId): Promise<Unit, Exception> = sqlitePersistenceManager.runQuery { connection ->
        val table = ConversationTable.getTablenameForContact(userId)

        connection.exec("DELETE FROM $table")
        resetConversationInfo(connection,  userId)

        Unit
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
