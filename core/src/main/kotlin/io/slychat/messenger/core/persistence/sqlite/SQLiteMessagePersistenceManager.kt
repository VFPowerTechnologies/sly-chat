package io.slychat.messenger.core.persistence.sqlite

import com.almworks.sqlite4java.SQLiteConnection
import com.almworks.sqlite4java.SQLiteConstants
import com.almworks.sqlite4java.SQLiteException
import com.almworks.sqlite4java.SQLiteStatement
import io.slychat.messenger.core.UserId
import io.slychat.messenger.core.persistence.InvalidMessageException
import io.slychat.messenger.core.persistence.MessageInfo
import io.slychat.messenger.core.persistence.MessagePersistenceManager
import nl.komponents.kovenant.Promise
import java.util.*

/** Depends on SQLiteContactsPersistenceManager for creating and deleting conversation tables. */
class SQLiteMessagePersistenceManager(
    private val sqlitePersistenceManager: SQLitePersistenceManager
) : MessagePersistenceManager {
    private fun insertMessage(connection: SQLiteConnection, userId: UserId, messageInfo: MessageInfo) {
        val table = ConversationTable.getTablenameForContact(userId)
        val sql = """
INSERT INTO $table
    (id, is_sent, timestamp, received_timestamp, ttl, is_delivered, message, n)
VALUES
    (?, ?, ?, ?, ?, ?, ?, (SELECT count(n)
                           FROM   $table
                           WHERE  timestamp = ?)+1)
"""

        try {
            connection.prepare(sql).use { stmt ->
                messageInfoToRow(messageInfo, stmt)
                stmt.bind(8, messageInfo.timestamp)
                stmt.step()
            }
        }
        catch (e: SQLiteException) {
            val message = e.message

            //ignores duplicates
            if (message != null) {
                if (e.baseErrorCode == SQLiteConstants.SQLITE_CONSTRAINT &&
                    message.contains("UNIQUE constraint failed: conv_\\d\\.id]".toRegex()))
                    return
            }

            throw e
        }
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

    private fun isMissingConvTableError(e: SQLiteException): Boolean =
        e.message?.let { "no such table: conv_" in it } ?: false

    override fun addMessage(userId: UserId, messageInfo: MessageInfo): Promise<MessageInfo, Exception> = sqlitePersistenceManager.runQuery { connection ->
        try {
            addMessageReal(connection, userId, messageInfo)
        }
        catch (e: SQLiteException) {
            if (isMissingConvTableError(e))
                throw InvalidMessageLevelException(userId)
            else
                throw e
        }

        messageInfo
    }

    override fun addMessages(userId: UserId, messages: Collection<MessageInfo>): Promise<List<MessageInfo>, Exception> = sqlitePersistenceManager.runQuery { connection ->
        if (messages.isNotEmpty()) {
            connection.withTransaction {
                messages.map { insertMessage(connection, userId, it) }
                updateConversationInfo(connection, userId, false, messages.last().message, messages.last().timestamp, messages.size)
                messages.toList()
            }
        }
        else
            listOf()
    }

    override fun markMessageAsDelivered(userId: UserId, messageId: String, timestamp: Long): Promise<MessageInfo, Exception> = sqlitePersistenceManager.runQuery { connection ->
        val table = ConversationTable.getTablenameForContact(userId)

        connection.prepare("UPDATE $table SET is_delivered=1, received_timestamp=? WHERE id=?").use { stmt ->
            stmt.bind(1, timestamp)
            stmt.bind(2, messageId)
            stmt.step()
        }

        if (connection.changes <= 0)
            throw InvalidMessageException(userId, messageId)

        connection.prepare("SELECT id, is_sent, timestamp, received_timestamp, ttl, is_delivered, message FROM $table WHERE id=?").use { stmt ->
            stmt.bind(1, messageId)
            if (!stmt.step())
                throw InvalidMessageException(userId, messageId)
            rowToMessageInfo(stmt)
        }
    }

    private fun queryLastMessages(connection: SQLiteConnection, userId: UserId, startingAt: Int, count: Int): List<MessageInfo> {
        val sql = "SELECT id, is_sent, timestamp, received_timestamp, ttl, is_delivered, message FROM ${ConversationTable.getTablenameForContact(userId)} ORDER BY timestamp DESC, n DESC LIMIT $count OFFSET $startingAt"
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

            val messages = connection.prepare("SELECT id, is_sent, timestamp, received_timestamp, ttl, is_delivered, message FROM $table WHERE is_delivered=0 ORDER BY timestamp, n").use { stmt ->
                stmt.map { rowToMessageInfo(it) }
            }

            if (messages.isNotEmpty())
                r[userId] = messages
        }

        r
    }

    private fun getLastConvoMessage(connection: SQLiteConnection, userId: UserId): MessageInfo? {
        val table = ConversationTable.getTablenameForContact(userId)

        return connection.prepare("SELECT id, is_sent, timestamp, received_timestamp, ttl, is_delivered, message FROM $table ORDER BY timestamp DESC, n DESC LIMIT 1").use { stmt ->
            if (!stmt.step())
                null
            else
                rowToMessageInfo(stmt)
        }
    }

    override fun deleteMessages(userId: UserId, messageIds: Collection<String>): Promise<Unit, Exception> = sqlitePersistenceManager.runQuery { connection ->
        if (!messageIds.isEmpty()) {
            val table = ConversationTable.getTablenameForContact(userId)

            connection.prepare("DELETE FROM $table WHERE id IN (${getPlaceholders(messageIds.size)})").use { stmt ->
                messageIds.forEachIndexed { i, messageId ->
                    stmt.bind(i+1, messageId)
                }

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
        stmt.bind(2, messageInfo.isSent)
        stmt.bind(3, messageInfo.timestamp)
        stmt.bind(4, messageInfo.receivedTimestamp)
        stmt.bind(5, messageInfo.ttl)
        stmt.bind(6, messageInfo.isDelivered)
        stmt.bind(7, messageInfo.message)
    }

    private fun rowToMessageInfo(stmt: SQLiteStatement): MessageInfo {
        val id = stmt.columnString(0)
        val isSent = stmt.columnInt(1) != 0
        val timestamp = stmt.columnLong(2)
        val receivedTimestamp = stmt.columnLong(3)
        val ttl = stmt.columnLong(4)
        val isDelivered = stmt.columnInt(5) != 0
        val message = stmt.columnString(6)

        return MessageInfo(id, message, timestamp, receivedTimestamp, isSent, isDelivered, ttl)
    }

}
