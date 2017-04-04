package io.slychat.messenger.core.persistence.sqlite

import com.almworks.sqlite4java.SQLiteConnection
import com.almworks.sqlite4java.SQLiteConstants
import com.almworks.sqlite4java.SQLiteException
import com.almworks.sqlite4java.SQLiteStatement
import com.fasterxml.jackson.databind.ObjectMapper
import io.slychat.messenger.core.UserId
import io.slychat.messenger.core.persistence.*
import nl.komponents.kovenant.Promise
import java.util.*

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

    private val objectMapper = ObjectMapper()

    override fun addMessages(conversationId: ConversationId, messages: Collection<ConversationMessageInfo>): Promise<Unit, Exception> {
        if (messages.isEmpty())
            return Promise.ofSuccess(Unit)

        return sqlitePersistenceManager.runQuery { connection ->
            connection.withTransaction {
                messages.forEach { insertMessage(connection, conversationId, it) }

                updateConversationInfo(connection, conversationId)
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
        stmt.bind(":id", messageInfo.id)
        stmt.bind(":speakerContactId", conversationMessageInfo.speaker)
        stmt.bind(":timestamp", messageInfo.timestamp)
        stmt.bind(":receivedTimestamp", messageInfo.receivedTimestamp)
        stmt.bind(":isRead", messageInfo.isRead)
        stmt.bind(":isExpired", messageInfo.isExpired)
        stmt.bind(":ttl", messageInfo.ttlMs)
        stmt.bind(":expiresAt", messageInfo.expiresAt)
        stmt.bind(":isDelivered", messageInfo.isDelivered)
        stmt.bind(":message", messageInfo.message)
        stmt.bind(":hasFailures", conversationMessageInfo.failures.isNotEmpty())
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

    override fun getUserConversation(userId: UserId): Promise<UserConversation?, Exception> = sqlitePersistenceManager.runQuery {
        conversationInfoUtils.getUserConversation(it, userId)
    }

    override fun getGroupConversation(groupId: GroupId): Promise<GroupConversation?, Exception> = sqlitePersistenceManager.runQuery {
        conversationInfoUtils.getGroupConversation(it, groupId)
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

            updateConversationInfo(connection, conversationId)
        }
    }

    private fun getUnreadCount(connection: SQLiteConnection, conversationId: ConversationId): Int {
        val tableName = ConversationTable.getTablename(conversationId)
        val sql = """
SELECT
    count(is_read)
FROM
    $tableName
WHERE
    is_read=0
"""

        return connection.withPrepared(sql) { stmt ->
            stmt.step()
            stmt.columnInt(0)
        }
    }

    private fun updateConversationInfo(connection: SQLiteConnection, conversationId: ConversationId) {
        val unreadCount = getUnreadCount(connection, conversationId)
        val lastMessageInfo = getLastConvoMessage(connection, conversationId)

        if (lastMessageInfo == null)
            insertOrReplaceNewConversationInfo(connection, conversationId)
        else {
            val sql = """
UPDATE
    conversation_info
SET
    last_speaker_contact_id=?,
    last_message=?,
    last_timestamp=?,
    unread_count=?
WHERE
    conversation_id=?
"""
            connection.withPrepared(sql) { stmt ->
                val info = lastMessageInfo.info
                val message = if (info.ttlMs <= 0)
                    info.message
                else
                    null

                stmt.bind(1, lastMessageInfo.speaker)
                stmt.bind(2, message)
                stmt.bind(3, info.timestamp)
                stmt.bind(4, unreadCount)
                stmt.bind(5, conversationId)

                stmt.step()
            }
        }
    }

    private fun isMessageIdValid(connection: SQLiteConnection, conversationId: ConversationId, messageId: String): Boolean {
        val tableName = ConversationTable.getTablename(conversationId)
        return connection.withPrepared("SELECT 1 FROM $tableName WHERE id=?") { stmt ->
            stmt.bind(1, messageId)
            stmt.step()
        }
    }

    private fun insertMessage(connection: SQLiteConnection, conversationId: ConversationId, conversationMessageInfo: ConversationMessageInfo) {
        val tableName = ConversationTable.getTablename(conversationId)

        val sql = """
INSERT INTO $tableName
    (id, speaker_contact_id, timestamp, received_timestamp, is_read, is_expired, ttl, expires_at, is_delivered, message, has_failures, n)
VALUES
    (:id, :speakerContactId, :timestamp, :receivedTimestamp, :isRead, :isExpired, :ttl, :expiresAt, :isDelivered, :message, :hasFailures,
        (SELECT count(n)
         FROM   $tableName
         WHERE  timestamp = :timestamp)+1)
"""
        try {
            connection.withPrepared(sql) { stmt ->
                conversationMessageInfoToRow(conversationMessageInfo, stmt)
                stmt.step()
            }

            insertAttachments(connection, conversationId, conversationMessageInfo.info.id, conversationMessageInfo.info.attachments)

            insertFailures(connection, conversationId, conversationMessageInfo.info.id, conversationMessageInfo.failures)
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

    private fun insertAttachmentRefs(connection: SQLiteConnection, conversationId: ConversationId, messageId: String, refs: ArrayList<Long>) {
        //language=SQLite
        val sql = """
INSERT INTO
    message_attachments
   (conversation_id, message_id, attachment_id)
VALUES
    (:conversationId, :messageId, :attachmentId)
"""

        connection.withPrepared(sql) { stmt ->
            stmt.bind(":conversationId", conversationId)
            stmt.bind(":messageId", messageId)

            refs.forEach {
                stmt.bind(":attachmentId", it)
                stmt.step()
                stmt.reset(false)
            }
        }
    }

    private fun insertAttachments(connection: SQLiteConnection, conversationId: ConversationId, messageId: String, attachments: List<MessageAttachmentInfo>) {
        //language=SQLite
        val sql = """
INSERT INTO
    attachments
    (display_name, is_inline, file_id)
VALUES
    (:displayName, :isInline, :fileId)
"""

        val refs = ArrayList<Long>()

        connection.withPrepared(sql) { stmt ->
            attachments.forEach {
                stmt.bind(":displayName", it.displayName)
                stmt.bind(":isInline", it.isInline)
                stmt.bind(":fileId", it.fileId)
                stmt.step()

                val attachmentId = connection.lastInsertId
                refs.add(attachmentId)

                stmt.reset()
            }
        }

        insertAttachmentRefs(connection, conversationId, messageId, refs)
    }

    private fun deleteFailures(connection: SQLiteConnection, conversationId: ConversationId, messageIds: Collection<String>) {
        connection.prepare("DELETE FROM message_failures WHERE conversation_id=? AND message_id IN (${getPlaceholders(messageIds.size)})").use { stmt ->
            stmt.bind(1, conversationId)

            messageIds.forEachIndexed { i, messageId ->
                stmt.bind(i + 2, messageId)
            }

            stmt.step()
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

                deleteFailures(connection, conversationId, messageIds)
            }
            catch (e: SQLiteException) {
                handleInvalidConversationException(e, conversationId)
            }

            deleteExpiringMessages(connection, conversationId, messageIds)

            updateConversationInfo(connection, conversationId)
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
    message,
    has_failures
FROM
    $tableName
WHERE
    is_expired = 0
ORDER BY
    timestamp DESC, n DESC
LIMIT
    1
"""
        return connection.withPrepared(sql) { stmt ->
            if (!stmt.step())
                null
            else
                rowToConversationMessageInfo(connection, stmt, conversationId)
        }
    }

    private fun getLastConvoTimestamp(connection: SQLiteConnection, conversationId: ConversationId): Long? {
        val tableName = ConversationTable.getTablename(conversationId)

        val sql = """
SELECT
    timestamp
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
                stmt.columnLong(0)
        }

    }

    override fun deleteAllMessages(conversationId: ConversationId): Promise<Long?, Exception> = sqlitePersistenceManager.runQuery { connection ->
        connection.withTransaction {
            val lastMessageTimestamp = getLastConvoTimestamp(connection, conversationId)

            //no last message
            if (lastMessageTimestamp == null) {
                null
            }
            else {
                val tableName = ConversationTable.getTablename(conversationId)
                connection.withPrepared("DELETE FROM $tableName", SQLiteStatement::step)

                deleteExpiringMessagesForConversation(connection, conversationId)

                deleteFailuresForConversation(connection, conversationId)

                insertOrReplaceNewConversationInfo(connection, conversationId)

                lastMessageTimestamp
            }
        }
    }

    private fun deleteFailuresForConversation(connection: SQLiteConnection, conversationId: ConversationId) {
        connection.withPrepared("DELETE FROM message_failures WHERE conversation_id=?") { stmt ->
            stmt.bind(1, conversationId)
            stmt.step()
        }
    }

    private fun deleteExpiringEntriesUntil(connection: SQLiteConnection, conversationId: ConversationId, timestamp: Long) {
        val tableName = ConversationTable.getTablename(conversationId)
        val sql = """
DELETE FROM
    expiring_messages
WHERE message_id IN (
    SELECT
        e.message_id
    FROM
        expiring_messages e
    JOIN
        $tableName c
    ON
        e.message_id=c.id
    WHERE
        e.conversation_id=?
    AND
        c.timestamp <= ?
)
"""
        connection.withPrepared(sql) { stmt ->
            stmt.bind(1, conversationId)
            stmt.bind(2, timestamp)
            stmt.step()
        }
    }

    private fun deleteAllConvoMessagesUntil(connection: SQLiteConnection, conversationId: ConversationId, timestamp: Long) {
        val tableName = ConversationTable.getTablename(conversationId)
        val sql = """
DELETE FROM
    $tableName
WHERE
    timestamp <= ?
"""

        connection.withPrepared(sql) { stmt ->
            stmt.bind(1, timestamp)
            stmt.step()
        }
    }

    override fun deleteAllMessagesUntil(conversationId: ConversationId, timestamp: Long): Promise<Unit, Exception> = sqlitePersistenceManager.runQuery { connection ->
        connection.withTransaction {
            deleteExpiringEntriesUntil(connection, conversationId, timestamp)
            deleteFailuresUntil(connection, conversationId, timestamp)
            deleteAllConvoMessagesUntil(connection, conversationId, timestamp)
            updateConversationInfo(connection, conversationId)
        }
    }

    private fun deleteFailuresUntil(connection: SQLiteConnection, conversationId: ConversationId, timestamp: Long) {
        val tableName = ConversationTable.getTablename(conversationId)
        val sql = """
DELETE FROM
    message_failures
WHERE
    conversation_id = ?
AND
    message_id IN (
        SELECT
            f.message_id
        FROM
            message_failures f
        JOIN
            $tableName c
        ON
            f.message_id=c.id
        WHERE
            f.conversation_id=?
        AND
            c.timestamp <= ?
    )
"""
        connection.withPrepared(sql) { stmt ->
            stmt.bind(1, conversationId)
            stmt.bind(2, conversationId)
            stmt.bind(3, timestamp)
            stmt.step()
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

    override fun markConversationAsRead(conversationId: ConversationId): Promise<List<String>, Exception> = sqlitePersistenceManager.runQuery { connection ->
        val unreadMessageIds = try {
            getUnreadMessageIds(connection, conversationId)
        }
        catch (e: SQLiteException) {
            if (isMissingConvTableError(e))
                throw InvalidConversationException(conversationId)

            throw e
        }

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

        unreadMessageIds
    }

    private fun getUnreadMessageIdsOrderedLimit(connection: SQLiteConnection, conversationId: ConversationId, limit: Int): List<String> {
        val tableName = ConversationTable.getTablename(conversationId)
        val sql = """
SELECT
    id
FROM
    $tableName
WHERE
    is_read = 0
ORDER BY
    timestamp DESC, n DESC
LIMIT
    $limit
"""
        return connection.withPrepared(sql) { stmt ->
            stmt.map { it.columnString(0) }
        }
    }

    private fun getUnreadMessageIds(connection: SQLiteConnection, conversationId: ConversationId): List<String> {
        val tableName = ConversationTable.getTablename(conversationId)
        val sql = """
SELECT
    id
FROM
    $tableName
WHERE
    is_read = 0
"""
        return connection.withPrepared(sql) { stmt ->
            stmt.map { it.columnString(0) }
        }
    }

    private fun markConversationMessagesAsRead(connection: SQLiteConnection, conversationId: ConversationId, messageIds: Collection<String>): List<String> {
        val tableName = ConversationTable.getTablename(conversationId)
        val sql = """
UPDATE
    $tableName
SET
    is_read=1
WHERE
    id=?
AND
    is_read=0
"""
        val r = ArrayList<String>()

        connection.withPrepared(sql) { stmt ->
            messageIds.forEach {
                stmt.bind(1, it)
                stmt.step()
                if (connection.changes > 0)
                    r.add(it)
                stmt.reset(true)
            }
        }

        return r
    }

    override fun markConversationMessagesAsRead(conversationId: ConversationId, messageIds: Collection<String>): Promise<List<String>, Exception> {
        if (messageIds.isEmpty())
            return Promise.ofSuccess(emptyList())

        return sqlitePersistenceManager.runQuery { connection ->
            try {
                connection.withTransaction {
                    val unreadMessageIds = markConversationMessagesAsRead(connection, conversationId, messageIds)

                    updateConversationInfo(connection, conversationId)

                    unreadMessageIds
                }
            }
            catch (e: SQLiteException) {
                if (isMissingConvTableError(e))
                    throw InvalidConversationException(conversationId)

                throw e
            }
        }
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
    message,
    has_failures
FROM
    $tableName
WHERE
    id=?
"""
        return connection.withPrepared(sql) { stmt ->
            stmt.bind(1, messageId)
            if (stmt.step())
                rowToConversationMessageInfo(connection, stmt, conversationId)
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
    message,
    has_failures
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
                stmt.map{
                    rowToConversationMessageInfo(connection, stmt, conversationId)
                }
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
    message,
    has_failures
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
            else {
                rowToConversationMessageInfo(connection, stmt, conversationId)
            }
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
    expires_at=0,
    is_read=1
WHERE
    id=?
"""
        connection.withPrepared(updateSql) { stmt ->
            messageIds.forEach { messageId ->
                stmt.bind(1, messageId)
                stmt.step()
                stmt.reset(false)
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
                stmt.reset(false)
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
                    updateConversationInfo(connection, conversationId)
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

        val conversationName = when (conversationId) {
            is ConversationId.Group -> getGroupName(connection, conversationId.id)
            is ConversationId.User -> getUserName(connection, conversationId.id)
        }

        val speakerId = conversationInfo.lastSpeaker

        val lastMessageData = if (conversationInfo.lastTimestamp != null) {
            val speakerName = speakerId?.let { getUserName(connection, speakerId) }
            LastMessageData(speakerName, speakerId, conversationInfo.lastMessage, conversationInfo.lastTimestamp)
        }
        else
            null

        val lastMessageIds = getUnreadMessageIdsOrderedLimit(connection, conversationId, 10)

        ConversationDisplayInfo(conversationId, conversationName, conversationInfo.unreadMessageCount, lastMessageIds, lastMessageData)
    }

    private fun insertFailures(connection: SQLiteConnection, conversationId: ConversationId, messageId: String, failures: Map<UserId, MessageSendFailure>) {
        if (failures.isEmpty())
            return

        val sql = """
INSERT OR REPLACE INTO
    message_failures
    (conversation_id, message_id, contact_id, reason)
VALUES
    (?, ?, ?, ?)
"""

        connection.withPrepared(sql) { stmt ->
            for ((userId, reason) in failures) {
                stmt.bind(1, conversationId)
                stmt.bind(2, messageId)
                stmt.bind(3, userId)
                stmt.bind(4, objectMapper.writeValueAsBytes(reason))
                stmt.step()
                stmt.reset()
            }
        }
    }

    private fun updateMessageFailureState(connection: SQLiteConnection, conversationId: ConversationId, messageId: String) {
        connection.withPrepared("UPDATE ${ConversationTable.getTablename(conversationId)} SET has_failures=1 WHERE id=?") { stmt ->
            stmt.bind(1, messageId)
            stmt.step()
        }
    }

    override fun addFailures(conversationId: ConversationId, messageId: String, failures: Map<UserId, MessageSendFailure>): Promise<ConversationMessageInfo, Exception> = sqlitePersistenceManager.runQuery { connection ->
        try {
            if (!isMessageIdValid(connection, conversationId, messageId))
                throw InvalidConversationMessageException(conversationId, messageId)
        }
        catch (e: SQLiteException) {
            handleInvalidConversationException(e, conversationId)
        }

        connection.withTransaction {
            insertFailures(connection, conversationId, messageId, failures)
            updateMessageFailureState(connection, conversationId, messageId)
            queryMessageInfo(connection, conversationId, messageId)!!
        }
    }

    private fun queryFailures(connection: SQLiteConnection, conversationId: ConversationId, messageId: String): Map<UserId, MessageSendFailure> {
        val sql = """
SELECT
    contact_id,
    reason
FROM
    message_failures
WHERE
    conversation_id=?
AND
    message_id=?
"""

        return connection.withPrepared(sql) { stmt ->
            stmt.bind(1, conversationId)
            stmt.bind(2, messageId)
            val r = HashMap<UserId, MessageSendFailure>()

            stmt.foreach {
                val userId = UserId(stmt.columnLong(0))
                val reason = objectMapper.readValue(stmt.columnBlob(1), MessageSendFailure::class.java)
                r[userId] = reason
            }

            r
        }
    }

    private fun selectAttachments(connection: SQLiteConnection, conversationId: ConversationId, messageId: String): List<MessageAttachmentInfo> {
        //language=SQLite
        val sql = """
SELECT
    a.display_name,
    a.file_id,
    a.is_inline
FROM
    message_attachments m
JOIN
    attachments a
ON
    a.id = m.attachment_id
WHERE
    m.conversation_id = :conversationId
AND
    m.message_id = :messageId
"""
        return connection.withPrepared(sql) { stmt ->
            stmt.bind(":conversationId", conversationId)
            stmt.bind(":messageId", messageId)
            stmt.map {
                MessageAttachmentInfo(
                    stmt.columnString(0),
                    stmt.columnString(1),
                    stmt.columnBool(2)
                )
            }
        }
    }

    private fun rowToConversationMessageInfo(connection: SQLiteConnection, stmt: SQLiteStatement, conversationId: ConversationId): ConversationMessageInfo {
        val id = stmt.columnString(0)
        val attachments = selectAttachments(connection, conversationId, id)

        val speaker = stmt.columnNullableLong(1)?.let(::UserId)
        val timestamp = stmt.columnLong(2)
        val receivedTimestamp = stmt.columnLong(3)
        val isRead = stmt.columnBool(4)
        val isDestroyed = stmt.columnBool(5)
        val ttl = stmt.columnLong(6)
        val expiresAt = stmt.columnLong(7)
        val isDelivered = stmt.columnBool(8)
        val message = stmt.columnString(9)

        val hasFailures = stmt.columnBool(10)
        val failures = if (hasFailures)
            queryFailures(connection, conversationId, id)
        else
            emptyMap()

        return ConversationMessageInfo(
            speaker,
            MessageInfo(
                id,
                message,
                timestamp,
                receivedTimestamp,
                speaker == null,
                isDelivered,
                isRead,
                isDestroyed,
                ttl,
                expiresAt,
                attachments
            ),
            failures
        )
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
    message,
    has_failures
FROM
    $tableName
ORDER BY
    timestamp, n
"""
        connection.withPrepared(sql) { stmt ->
            stmt.map {
                rowToConversationMessageInfo(connection, stmt, conversationId)
            }
        }
    }

    internal fun internalGetFailures(conversationId: ConversationId, messageId: String): Map<UserId, MessageSendFailure> = sqlitePersistenceManager.syncRunQuery {
        queryFailures(it, conversationId, messageId)
    }
}
