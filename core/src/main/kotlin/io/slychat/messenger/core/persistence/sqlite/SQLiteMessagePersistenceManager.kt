package io.slychat.messenger.core.persistence.sqlite

import com.almworks.sqlite4java.SQLiteConnection
import com.almworks.sqlite4java.SQLiteConstants
import com.almworks.sqlite4java.SQLiteException
import com.almworks.sqlite4java.SQLiteStatement
import io.slychat.messenger.core.UserId
import io.slychat.messenger.core.persistence.*
import nl.komponents.kovenant.Promise

internal fun deleteExpiringMessagesForConversation(connection: SQLiteConnection, conversationId: ConversationId) {
    connection.withPrepared("DELETE FROM expiring_messages WHERE conversation_id=?") { stmt ->
        stmt.bind(1, conversationId)
        stmt.step()
    }
}

/** Depends on SQLiteContactsPersistenceManager for creating and deleting conversation tables. */
class SQLiteMessagePersistenceManager(
    private val sqlitePersistenceManager: SQLitePersistenceManager
) : MessagePersistenceManager {
    private val conversationInfoUtils = ConversationInfoUtils()

    override fun addMessages(conversationId: ConversationId, messages: Collection<ConversationMessageInfo>): Promise<Unit, Exception> {
        if (messages.isEmpty())
            return Promise.ofSuccess(Unit)

        return sqlitePersistenceManager.runQuery { connection ->
            connection.withTransaction {
                messages.map { insertMessage(connection, conversationId, it) }
                val last = messages.last()
                val unreadInc = messages.filter { !it.info.isRead }.size

                updateConversationInfo(connection, conversationId, last.speaker, last.info.message, last.info.timestamp, unreadInc)
            }
        }
    }


    override fun getUndeliveredMessages(): Promise<Map<ConversationId, List<ConversationMessageInfo>>, Exception> = sqlitePersistenceManager.runQuery { connection ->
        TODO()
    }

    private fun isMissingConvTableError(e: SQLiteException): Boolean =
        e.message?.let { "no such table: conv_" in it } ?: false

    private fun conversationMessageInfoToRow(conversationMessageInfo: ConversationMessageInfo, stmt: SQLiteStatement) {
        val messageInfo = conversationMessageInfo.info
        stmt.bind(1, messageInfo.id)
        stmt.bind(2, conversationMessageInfo.speaker)
        stmt.bind(3, messageInfo.timestamp)
        stmt.bind(4, messageInfo.receivedTimestamp)
        stmt.bind(5, messageInfo.isRead)
        stmt.bind(6, messageInfo.isExpired)
        stmt.bind(7, messageInfo.ttl)
        stmt.bind(8, messageInfo.expiresAt)
        stmt.bind(9, messageInfo.isDelivered)
        stmt.bind(10, messageInfo.message)
    }

    /** Throws InvalidGroupException if group_conv table was missing, else rethrows the given exception. */
    //change this to invalidconversation or something instead
    private fun handleInvalidConversationException(e: SQLiteException, conversationId: ConversationId): Nothing {
        if (isMissingConvTableError(e))
            throw InvalidConversationException(conversationId)
        else
            throw e
    }

    override fun getConversationInfo(conversationId: ConversationId): Promise<ConversationInfo?, Exception> = sqlitePersistenceManager.runQuery { connection ->
        conversationInfoUtils.getConversationInfo(connection, conversationId)
    }

    override fun getAllGroupConversations(): Promise<List<GroupConversation>, Exception> = sqlitePersistenceManager.runQuery { connection ->
        conversationInfoUtils.getAllGroupConversations(connection)
    }

    override fun getAllUserConversations(): Promise<List<UserConversation>, Exception> = sqlitePersistenceManager.runQuery { connection ->
        conversationInfoUtils.getAllUserConversations(connection)
    }

    override fun addMessage(conversationId: ConversationId, conversationMessageInfo: ConversationMessageInfo): Promise<Unit, Exception> = sqlitePersistenceManager.runQuery { connection ->
        connection.withTransaction {
            try {
                insertMessage(connection, conversationId, conversationMessageInfo)
            }
            catch (e: SQLiteException) {
                if (isMissingConvTableError(e)) {
                    when (conversationId) {
                        is ConversationId.User -> throw InvalidMessageLevelException(conversationId.id)
                        is ConversationId.Group -> throw InvalidConversationException(conversationId)
                    }
                }
                else
                    throw e
            }

            val messageInfo = conversationMessageInfo.info

            val unreadInc = if (messageInfo.isRead) 0 else 1
            updateConversationInfo(connection, conversationId, conversationMessageInfo.speaker, messageInfo.message, messageInfo.timestamp, unreadInc)
        }
    }

    private fun updateConversationInfo(connection: SQLiteConnection, conversationId: ConversationId, speaker: UserId?, lastMessage: String?, lastTimestamp: Long?, unreadIncrement: Int) {
        val unreadCountFragment = if (unreadIncrement > 0) "unread_count=unread_count+$unreadIncrement," else ""

        connection.withPrepared("UPDATE conversation_info SET $unreadCountFragment last_speaker_contact_id=?, last_message=?, last_timestamp=? WHERE conversation_id=?") { stmt ->
            stmt.bind(1, speaker)
            stmt.bind(2, lastMessage)
            stmt.bind(3, lastTimestamp)
            stmt.bind(4, conversationId)
            stmt.step()
        }
    }

    private fun insertMessage(connection: SQLiteConnection, conversationId: ConversationId, conversationMessageInfo: ConversationMessageInfo) {
        val tableName = ConversationTable.getTablename(conversationId)
        val sql =
            """
INSERT INTO $tableName
    (id, speaker_contact_id, timestamp, received_timestamp, is_read, is_expired, ttl, expires_at, is_delivered, message, n)
VALUES
    (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, (SELECT count(n)
                                    FROM   $tableName
                                    WHERE  timestamp = ?)+1)
"""
        try {
            connection.withPrepared(sql) { stmt ->
                conversationMessageInfoToRow(conversationMessageInfo, stmt)
                stmt.bind(11, conversationMessageInfo.info.timestamp)
                stmt.step()
            }
        }
        catch (e: SQLiteException) {
            val message = e.message

            //ignores duplicates
            if (message != null) {
                if (e.baseErrorCode == SQLiteConstants.SQLITE_CONSTRAINT &&
                    message.contains("UNIQUE constraint failed: conv_[^.]+.id]".toRegex()))
                    return
            }

            throw e
        }
    }

    override fun deleteMessages(conversationId: ConversationId, messageIds: Collection<String>): Promise<Unit, Exception> = sqlitePersistenceManager.runQuery { connection ->
        if (messageIds.isNotEmpty()) {
            val tableName = ConversationTable.getTablename(conversationId)

            try {
                connection.prepare("DELETE FROM $tableName WHERE id IN (${getPlaceholders(messageIds.size)})").use { stmt ->
                    messageIds.forEachIndexed { i, messageId ->
                        stmt.bind(i + 1, messageId)
                    }

                    stmt.step()
                }
            }
            catch (e: SQLiteException) {
                handleInvalidConversationException(e, conversationId)
            }

            deleteExpiringMessages(connection, conversationId, messageIds)

            val lastMessage = getLastConvoMessage(connection, conversationId)
            if (lastMessage == null)
                insertOrReplaceNewConversationInfo(connection, conversationId)
            else {
                val info = lastMessage.info
                updateConversationInfo(connection, conversationId, lastMessage.speaker, info.message, info.timestamp, 0)
            }
        }
    }

    private fun getLastConvoMessage(connection: SQLiteConnection, conversationId: ConversationId): ConversationMessageInfo? {
        val tableName = ConversationTable.getTablename(conversationId)

        val sql =
            """
SELECT
    id,
    speaker_contact_id,
    timestamp,
    received_timestamp,
    is_read,
    is_expired,
    ttl,
    expires_at,
    is_delivered,
    message
FROM
    $tableName
ORDER BY
    timestamp DESC, n DESC
LIMIT
    1
"""
        return connection.withPrepared(sql) { stmt ->
            if (!stmt.step())
                null
            else
                rowToConversationMessageInfo(stmt)
        }
    }

    override fun deleteAllMessages(conversationId: ConversationId): Promise<Unit, Exception> = sqlitePersistenceManager.runQuery { connection ->
        connection.withTransaction {
            val tableName = ConversationTable.getTablename(conversationId)
            connection.withPrepared("DELETE FROM $tableName") { stmt ->
                stmt.step()
            }

            deleteExpiringMessagesForConversation(connection, conversationId)

            insertOrReplaceNewConversationInfo(connection, conversationId)
        }
    }

    override fun markMessageAsDelivered(conversationId: ConversationId, messageId: String, timestamp: Long): Promise<ConversationMessageInfo?, Exception> = sqlitePersistenceManager.runQuery { connection ->
        val tableName = ConversationTable.getTablename(conversationId)

        val sql = "UPDATE $tableName SET is_delivered=1, received_timestamp=? WHERE id=?"

        val currentInfo = try {
            getConversationMessageInfo(connection, conversationId, messageId) ?: throw InvalidConversationMessageException(conversationId, messageId)
        }
        catch (e: SQLiteException) {
            //FIXME
            handleInvalidConversationException(e, conversationId)
        }

        if (!currentInfo.info.isDelivered) {
            try {
                connection.withPrepared(sql) { stmt ->
                    stmt.bind(1, timestamp)
                    stmt.bind(2, messageId)
                    stmt.step()
                }
            }
            catch (e: SQLiteException) {
                handleInvalidConversationException(e, conversationId)
            }

            if (connection.changes <= 0)
                throw InvalidConversationMessageException(conversationId, messageId)

            getConversationMessageInfo(connection, conversationId, messageId) ?: throw InvalidConversationMessageException(conversationId, messageId)
        }
        else
            null
    }

    override fun markConversationAsRead(conversationId: ConversationId): Promise<Unit, Exception> = sqlitePersistenceManager.runQuery { connection ->
        connection.withTransaction {
            connection.withPrepared("UPDATE conversation_info set unread_count=0 WHERE conversation_id=?") { stmt ->
                stmt.bind(1, conversationId)
                stmt.step()
            }

            if (connection.changes == 0)
                throw InvalidConversationException(conversationId)

            val tableName = ConversationTable.getTablename(conversationId)
            connection.exec("UPDATE $tableName SET is_read=1 WHERE is_read=0")
        }

        Unit
    }

    private fun getConversationMessageInfo(connection: SQLiteConnection, conversationId: ConversationId, messageId: String): ConversationMessageInfo? {
        val tableName = ConversationTable.getTablename(conversationId)
        val sql =
            """
SELECT
    id,
    speaker_contact_id,
    timestamp,
    received_timestamp,
    is_read,
    is_expired,
    ttl,
    expires_at,
    is_delivered,
    message
FROM
    $tableName
WHERE
    id=?
"""
        return connection.withPrepared(sql) { stmt ->
            stmt.bind(1, messageId)
            if (stmt.step())
                rowToConversationMessageInfo(stmt)
            else
                null
        }
    }

    override fun getLastMessages(conversationId: ConversationId, startingAt: Int, count: Int): Promise<List<ConversationMessageInfo>, Exception> = sqlitePersistenceManager.runQuery { connection ->
        val tableName = ConversationTable.getTablename(conversationId)
        val sql =
            """
SELECT
    id,
    speaker_contact_id,
    timestamp,
    received_timestamp,
    is_read,
    is_expired,
    ttl,
    expires_at,
    is_delivered,
    message
FROM
    $tableName
ORDER BY
    timestamp DESC, n DESC
LIMIT
    $count
OFFSET
    $startingAt
"""
        try {
            connection.withPrepared(sql) { stmt ->
                stmt.map { rowToConversationMessageInfo(it) }
            }
        }
        catch (e: SQLiteException) {
            handleInvalidConversationException(e, conversationId)
        }
    }

    private fun queryMessageInfo(connection: SQLiteConnection, conversationId: ConversationId, messageId: String): ConversationMessageInfo? {
        val tableName = ConversationTable.getTablename(conversationId)

        val sql =
            """
SELECT
    id,
    speaker_contact_id,
    timestamp,
    received_timestamp,
    is_read,
    is_expired,
    ttl,
    expires_at,
    is_delivered,
    message
FROM
    $tableName
WHERE
    id=?
ORDER BY
    timestamp DESC, n DESC
LIMIT
    1
"""
        return connection.withPrepared(sql) { stmt ->
            stmt.bind(1, messageId)

            if (!stmt.step())
                null
            else
                rowToConversationMessageInfo(stmt)
        }
    }

    override fun get(conversationId: ConversationId, messageId: String): Promise<ConversationMessageInfo?, Exception> = sqlitePersistenceManager.runQuery {
        queryMessageInfo(it, conversationId, messageId)
    }

    private fun updateMessageSetExpired(connection: SQLiteConnection, conversationId: ConversationId, messageIds: Collection<String>) {
        val tableName = ConversationTable.getTablename(conversationId)
        val updateSql = """
UPDATE
    $tableName
SET
    message="",
    is_expired=1,
    ttl=0,
    expires_at=0
WHERE
    id=?
"""
        connection.withPrepared(updateSql) { stmt ->
            messageIds.forEach { messageId ->
                stmt.bind(1, messageId)
                stmt.step()
            }
        }
    }

    private fun deleteExpiringMessages(connection: SQLiteConnection, conversationId: ConversationId, messageIds: Collection<String>) {
        val deleteSql = """
DELETE FROM
    expiring_messages
WHERE
    conversation_id=?
AND
    message_id=?
"""

        connection.withPrepared(deleteSql) { stmt ->
            stmt.bind(1, conversationId)

            messageIds.forEach { messageId ->
                stmt.bind(2, messageId)
                stmt.step()
            }
        }
    }

    override fun expireMessages(messages: Map<ConversationId, Collection<String>>): Promise<Unit, Exception> {
        if (messages.isEmpty())
            return Promise.ofSuccess(Unit)

        return sqlitePersistenceManager.runQuery { connection ->
            connection.withTransaction {
                for ((conversationId, messageIds) in messages) {
                    updateMessageSetExpired(connection, conversationId, messageIds)
                    deleteExpiringMessages(connection, conversationId, messageIds)
                }
            }
        }
    }

    private fun updateMessageSetExpiring(connection: SQLiteConnection, conversationId: ConversationId, messageId: String, expiresAt: Long) {
        val tableName = ConversationTable.getTablename(conversationId)

        val sql = """
UPDATE
    $tableName
SET
    expires_at=?
WHERE
    id=?
"""
        connection.withPrepared(sql) { stmt ->
            stmt.bind(1, expiresAt)
            stmt.bind(2, messageId)
            stmt.step()
            if (connection.changes <= 0)
                throw InvalidConversationMessageException(conversationId, messageId)
        }
    }

    private fun insertExpiringMessage(connection: SQLiteConnection, conversationId: ConversationId, messageId: String, expiresAt: Long) {
        val sql = """
INSERT INTO
    expiring_messages
    (conversation_id, message_id, expires_at)
VALUES
    (?, ?, ?)
"""
        connection.withPrepared(sql) { stmt ->
            stmt.bind(1, conversationId)
            stmt.bind(2, messageId)
            stmt.bind(3, expiresAt)
            stmt.step()
        }
    }

    override fun setExpiration(conversationId: ConversationId, messageId: String, expiresAt: Long): Promise<Boolean, Exception> {
        require(expiresAt > 0) { "expiresAt must be > 0, got $expiresAt" }

        return sqlitePersistenceManager.runQuery { connection ->
            try {
                connection.withTransaction {
                    insertExpiringMessage(connection, conversationId, messageId, expiresAt)
                    updateMessageSetExpiring(connection, conversationId, messageId, expiresAt)
                }

                true
            }
            catch (e: SQLiteException) {
                val message = e.message

                if (message == null)
                    throw e
                else {
                    if (e.baseErrorCode == SQLiteConstants.SQLITE_CONSTRAINT &&
                        message.contains("UNIQUE constraint failed: expiring_messages.conversation_id, expiring_messages.message_id".toRegex()))
                        false
                    else
                        throw e
                }
            }
        }
    }

    override fun getMessagesAwaitingExpiration(): Promise<List<ExpiringMessage>, Exception> = sqlitePersistenceManager.runQuery { connection ->
        val sql = """
SELECT
    conversation_id,
    message_id,
    expires_at
FROM
    expiring_messages
"""

        connection.withPrepared(sql) { stmt ->
            stmt.map { rowToExpiringMessage(stmt) }
        }
    }

    private fun rowToExpiringMessage(stmt: SQLiteStatement): ExpiringMessage {
        return ExpiringMessage(
            stmt.columnConversationId(0),
            stmt.columnString(1),
            stmt.columnLong(2)
        )
    }

    private fun getGroupName(connection: SQLiteConnection, groupId: GroupId): String {
        val sql = """
SELECT
    name
FROM
    groups
WHERE
    id=?
"""
        return connection.withPrepared(sql) { stmt ->
            stmt.bind(1, groupId)
            if (!stmt.step())
                throw InvalidGroupException(groupId)

            stmt.columnString(0)
        }
    }

    private fun getUserName(connection: SQLiteConnection, userId: UserId): String {
        val sql = """
SELECT
    name
FROM
    contacts
WHERE
    id=?
"""
        return connection.withPrepared(sql) { stmt ->
            stmt.bind(1, userId)
            if (!stmt.step())
                throw InvalidContactException(userId)

            stmt.columnString(0)
        }
    }

    override fun getConversationDisplayInfo(conversationId: ConversationId): Promise<ConversationDisplayInfo, Exception> = sqlitePersistenceManager.runQuery { connection ->
        val conversationInfo = conversationInfoUtils.getConversationInfo(connection, conversationId) ?: throw InvalidConversationException(conversationId)

        val groupName = when (conversationId) {
            is ConversationId.Group -> getGroupName(connection, conversationId.id)
            else -> null
        }

        val lastMessageData = if (conversationInfo.lastSpeaker != null) {
            val speakerName = getUserName(connection, conversationInfo.lastSpeaker)
            LastMessageData(speakerName, conversationInfo.lastMessage!!, conversationInfo.lastTimestamp!!)
        }
        else
            null

        ConversationDisplayInfo(conversationId, groupName, conversationInfo.unreadMessageCount, lastMessageData)
    }

    /* test use only */
    internal fun internalMessageExists(conversationId: ConversationId, messageId: String): Boolean = sqlitePersistenceManager.syncRunQuery { connection ->
        connection.withPrepared("SELECT 1 FROM ${ConversationTable.getTablename(conversationId)} WHERE id=?") { stmt ->
            stmt.bind(1, messageId)
            stmt.step()
        }
    }

    internal fun internalGetAllMessages(conversationId: ConversationId): List<ConversationMessageInfo> = sqlitePersistenceManager.syncRunQuery { connection ->
        val tableName = ConversationTable.getTablename(conversationId)
        val sql =
            """
SELECT
    id,
    speaker_contact_id,
    timestamp,
    received_timestamp,
    is_read,
    is_expired,
    ttl,
    expires_at,
    is_delivered,
    message
FROM
    $tableName
ORDER BY
    timestamp, n
"""
        connection.withPrepared(sql) { stmt ->
            stmt.map { rowToConversationMessageInfo(it) }
        }
    }
}
