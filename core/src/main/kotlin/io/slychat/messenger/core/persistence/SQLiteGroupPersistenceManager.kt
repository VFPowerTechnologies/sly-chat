package io.slychat.messenger.core.persistence

import com.almworks.sqlite4java.SQLiteConnection
import com.almworks.sqlite4java.SQLiteException
import com.almworks.sqlite4java.SQLiteStatement
import io.slychat.messenger.core.UserId
import io.slychat.messenger.core.currentTimestamp
import io.slychat.messenger.core.persistence.sqlite.*
import nl.komponents.kovenant.Promise
import java.util.*

class SQLiteGroupPersistenceManager(
    private val sqlitePersistenceManager: SQLitePersistenceManager
) : GroupPersistenceManager {
    private fun groupMembershipLevelToInt(membershipLevel: GroupMembershipLevel): Int =
        when (membershipLevel) {
            GroupMembershipLevel.BLOCKED -> 0
            GroupMembershipLevel.PARTED -> 1
            GroupMembershipLevel.JOINED -> 2
        }

    private fun intToGroupMembershipLevel(i: Int): GroupMembershipLevel =
        when (i) {
            0 -> GroupMembershipLevel.BLOCKED
            1 -> GroupMembershipLevel.PARTED
            2 -> GroupMembershipLevel.JOINED
            else -> throw IllegalArgumentException("Invalid integer value for MembershipLevel: $i")
        }

    override fun getGroupList(): Promise<List<GroupInfo>, Exception> {
        TODO()
    }

    override fun getGroupInfo(groupId: GroupId): Promise<GroupInfo?, Exception> = sqlitePersistenceManager.runQuery { connection ->
        queryGroupInfo(connection, groupId)
    }

    override fun getGroupMembers(groupId: GroupId): Promise<Set<UserId>, Exception> = sqlitePersistenceManager.runQuery { connection ->
        throwIfGroupIsInvalid(connection, groupId)
        queryGroupMembers(connection, groupId)
    }

    override fun getAllGroupConversationInfo(): Promise<List<GroupConversationInfo>, Exception> {
        TODO()
    }

    override fun addMembers(groupId: GroupId, users: Set<UserId>): Promise<Set<UserId>, Exception> = sqlitePersistenceManager.runQuery { connection ->
        val currentMembers = queryGroupMembers(connection, groupId)

        val newMembers = HashSet(users)
        newMembers.removeAll(currentMembers)

        insertGroupMembers(connection, groupId, newMembers)

        newMembers
    }

    override fun removeMember(groupId: GroupId, userId: UserId): Promise<Boolean, Exception> = sqlitePersistenceManager.runQuery { connection ->
        throwIfGroupIsInvalid(connection, groupId)

        connection.withPrepared("DELETE FROM group_members WHERE group_id=? AND contact_id=?") { stmt ->
            stmt.bind(1, groupId)
            stmt.bind(2, userId)

            stmt.step()

        }

        connection.changes > 0
    }

    private fun throwIfGroupIsInvalid(connection: SQLiteConnection, groupId: GroupId) {
        val tableName = GroupConversationTable.getTablename(groupId)
        try {
            connection.withPrepared("SELECT 1 FROM $tableName") { stmt ->
                stmt.step()
            }
        }
        catch (e: SQLiteException) {
            if (isMissingGroupConvTableError(e))
                throw InvalidGroupException(groupId)
            else
                throw e
        }
    }

    override fun isUserMemberOf(groupId: GroupId, userId: UserId): Promise<Boolean, Exception> = sqlitePersistenceManager.runQuery { connection ->
        throwIfGroupIsInvalid(connection, groupId)

        connection.withPrepared("SELECT 1 FROM group_members WHERE group_id=? AND contact_id=?") { stmt ->
            stmt.bind(1, groupId)
            stmt.bind(2, userId)
            stmt.step()
        }
    }

    override fun joinGroup(groupInfo: GroupInfo, members: Set<UserId>): Promise<Unit, Exception> = sqlitePersistenceManager.runQuery { connection ->
        require(groupInfo.membershipLevel == GroupMembershipLevel.JOINED) { "Invalid membershipLevel: ${groupInfo.membershipLevel}"}

        val maybeInfo = queryGroupInfo(connection, groupInfo.id)

        //do nothing if we're already joined
        if (maybeInfo != null && maybeInfo.membershipLevel == GroupMembershipLevel.JOINED) {
            return@runQuery
        }
        else {
            connection.withTransaction {
                //rejoin (this should already be empty anyways from parting/blocking)
                if (maybeInfo != null)
                    clearMemberList(connection, groupInfo.id)

                insertOrReplaceGroupInfo(connection, groupInfo)
                insertOrReplaceNewGroupConversationInfo(connection, groupInfo.id)
                createGroupConversationTable(connection, groupInfo.id)
                insertGroupMembers(connection, groupInfo.id, members)
            }
        }
    }

    private fun insertOrReplaceNewGroupConversationInfo(connection: SQLiteConnection, id: GroupId) {
        val sql =
"""
INSERT OR REPLACE INTO group_conversation_info
    (group_id, last_speaker_contact_id, unread_count, last_message, last_timestamp)
VALUES
    (?, null, 0, null, null)
"""
        connection.withPrepared(sql) { stmt ->
            stmt.bind(1, id)
            stmt.step()
        }
    }

    private fun insertGroupMembers(connection: SQLiteConnection, id: GroupId, members: Set<UserId>) {
        try {
            connection.batchInsert("INSERT INTO group_members (group_id, contact_id) VALUES (?, ?)", members) { stmt, member ->
                stmt.bind(1, id)
                stmt.bind(2, member)
            }
        }
        catch (e: SQLiteException) {
            //XXX
            //since we have two fks in here, this is either a missing group, or a missing contact
            //sadly, sqlite doesn't report which fk causes the issue, and there's no way to name fks
            //so we just assume the group is missing here, since that would be the more common case in normal operations
            val isFkError = e.message?.let { "FOREIGN KEY constraint failed" in it } ?: false

            if (isFkError)
                throw InvalidGroupException(id)
            else
                throw e
        }
    }

    private fun createGroupConversationTable(connection: SQLiteConnection, id: GroupId) {
        GroupConversationTable.create(connection, id)
    }

    private fun insertOrReplaceGroupInfo(connection: SQLiteConnection, groupInfo: GroupInfo) {
        connection.withPrepared("INSERT OR REPLACE INTO groups (id, name, membership_level) VALUES (?, ?, ?)") { stmt ->
            stmt.bind(1, groupInfo.id)
            stmt.bind(2, groupInfo.name)
            stmt.bind(3, groupMembershipLevelToInt(groupInfo.membershipLevel))
            stmt.step()
        }
    }

    private fun clearMemberList(connection: SQLiteConnection, id: GroupId) {
        connection.withPrepared("DELETE FROM group_members WHERE group_id=?") { stmt ->
            stmt.bind(1, id)
            stmt.step()
        }
    }

    private fun queryGroupMembers(connection: SQLiteConnection, id: GroupId): Set<UserId> {
        return connection.withPrepared("SELECT contact_id FROM group_members WHERE group_id=?") { stmt ->
            stmt.bind(1, id)

            stmt.mapToSet { UserId(stmt.columnLong(0)) }
        }
    }

    private fun queryGroupInfoOrThrow(connection: SQLiteConnection, id: GroupId): GroupInfo =
        queryGroupInfo(connection, id) ?: throw InvalidGroupException(id)

    private fun queryGroupInfo(connection: SQLiteConnection, id: GroupId): GroupInfo? {
        return connection.withPrepared("SELECT id, name, membership_level FROM groups WHERE id=?") { stmt ->
            stmt.bind(1, id)

            if (stmt.step())
                rowToGroupInfo(stmt)
            else
                null
        }
    }

    private fun rowToGroupInfo(stmt: SQLiteStatement): GroupInfo? {
        return GroupInfo(
            GroupId(stmt.columnString(0)),
            stmt.columnString(1),
            false,
            intToGroupMembershipLevel(stmt.columnInt(2))
        )
    }

    private fun rowToGroupConversationInfo(stmt: SQLiteStatement, id: GroupId): GroupConversationInfo {
        return GroupConversationInfo(
            id,
            stmt.columnNullableLong(0)?.let { UserId(it) },
            stmt.columnInt(1),
            stmt.columnString(2),
            stmt.columnNullableLong(3)
        )
    }

    private fun rowToGroupMessageInfo(stmt: SQLiteStatement): GroupMessageInfo {
        val speaker = stmt.columnNullableLong(1)?.let { UserId(it) }
        return GroupMessageInfo(
            speaker,
            MessageInfo(
                stmt.columnString(0),
                stmt.columnString(6),
                stmt.columnLong(2),
                stmt.columnLong(3),
                speaker == null,
                stmt.columnBool(5),
                stmt.columnLong(4)
            )
        )
    }

    private fun updateMembershipLevel(connection: SQLiteConnection, groupId: GroupId, membershipLevel: GroupMembershipLevel) {
        connection.withPrepared("UPDATE groups set membership_level=? WHERE id=?") { stmt ->
            stmt.bind(1, groupMembershipLevelToInt(membershipLevel))
            stmt.bind(2, groupId)
            stmt.step()
        }
    }

    override fun partGroup(groupId: GroupId): Promise<Boolean, Exception> = sqlitePersistenceManager.runQuery { connection ->
        val groupInfo = queryGroupInfoOrThrow(connection, groupId)

        when (groupInfo.membershipLevel) {
            GroupMembershipLevel.PARTED -> false

            GroupMembershipLevel.BLOCKED -> false

            GroupMembershipLevel.JOINED -> {
                connection.withTransaction {
                    clearMemberList(connection, groupId)

                    updateMembershipLevel(connection, groupId, GroupMembershipLevel.PARTED)
                }

                true
            }
        }
    }

    override fun getBlockList(): Promise<Set<GroupId>, Exception> = sqlitePersistenceManager.runQuery { connection ->
        connection.withPrepared("SELECT id FROM groups WHERE membership_level=?") { stmt ->
            stmt.bind(1, groupMembershipLevelToInt(GroupMembershipLevel.BLOCKED))

            stmt.mapToSet { GroupId(it.columnString(0)) }
        }
    }

    fun isBlocked(groupId: GroupId): Promise<Boolean, Exception> {
        TODO()
    }

    override fun blockGroup(groupId: GroupId): Promise<Unit, Exception> = sqlitePersistenceManager.runQuery { connection ->
        val groupInfo = queryGroupInfoOrThrow(connection, groupId)

        if (groupInfo.membershipLevel == GroupMembershipLevel.BLOCKED)
            return@runQuery

        connection.withTransaction {
            clearMemberList(connection, groupId)
            updateMembershipLevel(connection, groupId, GroupMembershipLevel.BLOCKED)
        }
    }

    override fun unblockGroup(groupId: GroupId): Promise<Unit, Exception> = sqlitePersistenceManager.runQuery { connection ->
        val groupInfo = queryGroupInfoOrThrow(connection, groupId)

        when (groupInfo.membershipLevel) {
            GroupMembershipLevel.JOINED -> {}
            GroupMembershipLevel.PARTED -> {}
            GroupMembershipLevel.BLOCKED -> {
                updateMembershipLevel(connection, groupId, GroupMembershipLevel.PARTED)
            }
        }
    }

    private fun isMissingGroupConvTableError(e: SQLiteException): Boolean =
        e.message?.let { "no such table: group_conv_" in it } ?: false

    override fun addMessage(groupId: GroupId, groupMessageInfo: GroupMessageInfo): Promise<GroupMessageInfo, Exception> = sqlitePersistenceManager.runQuery { connection ->
        connection.withTransaction {
            try {
                insertMessage(connection, groupId, groupMessageInfo)
            }
            catch (e: SQLiteException) {
                if (isMissingGroupConvTableError(e))
                    throw InvalidGroupException(groupId)
                else
                    throw e
            }

            val messageInfo = groupMessageInfo.info

            updateConversationInfo(connection, groupId, groupMessageInfo.speaker, messageInfo.message, messageInfo.timestamp, 1)
        }

        groupMessageInfo
    }

    private fun updateConversationInfo(connection: SQLiteConnection, groupId: GroupId, speaker: UserId?, lastMessage: String?, lastTimestamp: Long?, unreadIncrement: Int) {
        val unreadCountFragment = if (speaker != null) "unread_count=unread_count+$unreadIncrement," else ""

        connection.withPrepared("UPDATE group_conversation_info SET $unreadCountFragment last_speaker_contact_id=?, last_message=?, last_timestamp=? WHERE group_id=?") { stmt ->
            stmt.bind(1, speaker)
            stmt.bind(2, lastMessage)
            if (lastTimestamp != null)
                stmt.bind(3, lastTimestamp)
            else
                stmt.bindNull(3)
            stmt.bind(4, groupId)
            stmt.step()
        }
    }

    private fun insertMessage(connection: SQLiteConnection, groupId: GroupId, groupMessageInfo: GroupMessageInfo) {
        val tableName = GroupConversationTable.getTablename(groupId)
        val sql =
"""
INSERT INTO $tableName
    (id, speaker_contact_id, timestamp, received_timestamp, ttl, is_delivered, message, n)
VALUES
    (?, ?, ?, ?, ?, ?, ?, (SELECT count(n)
                           FROM   $tableName
                           WHERE  timestamp = ?)+1)
"""
        connection.withPrepared(sql) { stmt ->
            groupMessageInfoToRow(groupMessageInfo, stmt)
            stmt.bind(8, groupMessageInfo.info.timestamp)
            stmt.step()
        }
    }

    private fun groupMessageInfoToRow(groupMessageInfo: GroupMessageInfo, stmt: SQLiteStatement) {
        val messageInfo = groupMessageInfo.info
        stmt.bind(1, messageInfo.id)
        stmt.bind(2, groupMessageInfo.speaker)
        stmt.bind(3, messageInfo.timestamp)
        stmt.bind(4, messageInfo.receivedTimestamp)
        stmt.bind(5, messageInfo.ttl)
        stmt.bind(6, messageInfo.isDelivered.toInt())
        stmt.bind(7, messageInfo.message)
    }

    override fun deleteMessages(groupId: GroupId, messageIds: Collection<String>): Promise<Unit, Exception> = sqlitePersistenceManager.runQuery { connection ->
        if (messageIds.isNotEmpty()) {
            val tableName = GroupConversationTable.getTablename(groupId)

            connection.withTransaction {
                connection.prepare("DELETE FROM $tableName WHERE id IN (${getPlaceholders(messageIds.size)})").use { stmt ->
                    messageIds.forEachIndexed { i, messageId ->
                        stmt.bind(i + 1, messageId)
                    }

                    stmt.step()
                }
            }

            val lastMessage = getLastConvoMessage(connection, groupId)
            if (lastMessage == null)
                insertOrReplaceNewGroupConversationInfo(connection, groupId)
            else {
                val info = lastMessage.info
                updateConversationInfo(connection, groupId, lastMessage.speaker, info.message, info.timestamp, 0)
            }
        }
    }

    private fun getLastConvoMessage(connection: SQLiteConnection, groupId: GroupId): GroupMessageInfo? {
        val tableName = GroupConversationTable.getTablename(groupId)

        val sql =
"""
SELECT
    id,
    speaker_contact_id,
    timestamp,
    received_timestamp,
    ttl,
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
                rowToGroupMessageInfo(stmt)
        }
    }

    override fun deleteAllMessages(groupId: GroupId): Promise<Unit, Exception> = sqlitePersistenceManager.runQuery { connection ->
        connection.withTransaction {
            val tableName = GroupConversationTable.getTablename(groupId)
            connection.withPrepared("DELETE FROM $tableName") { stmt ->
                stmt.step()
                Unit
            }

            insertOrReplaceNewGroupConversationInfo(connection, groupId)
        }
    }

    override fun markMessageAsDelivered(groupId: GroupId, messageId: String): Promise<GroupMessageInfo, Exception> = sqlitePersistenceManager.runQuery { connection ->
        val tableName = GroupConversationTable.getTablename(groupId)
        val sql = "UPDATE $tableName SET is_delivered=1, received_timestamp=? WHERE id=?"

        try {
            connection.withPrepared(sql) { stmt ->
                stmt.bind(1, currentTimestamp())
                stmt.bind(2, messageId)
                stmt.step()
            }
        }
        catch (e: SQLiteException) {
            if (isMissingGroupConvTableError(e))
                throw InvalidGroupException(groupId)
            else
                throw e
        }

        if (connection.changes <= 0)
            throw InvalidGroupMessageException(groupId, messageId)

        getGroupMessageInfo(connection, groupId, messageId) ?: throw InvalidGroupMessageException(groupId, messageId)
    }

    private fun getGroupMessageInfo(connection: SQLiteConnection, groupId: GroupId, messageId: String): GroupMessageInfo? {
        val tableName = GroupConversationTable.getTablename(groupId)
        val sql =
            """
SELECT
    id,
    speaker_contact_id,
    timestamp,
    received_timestamp,
    ttl,
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
                rowToGroupMessageInfo(stmt)
            else
                null
        }
    }

    override fun getLastMessages(groupId: GroupId, startingAt: Int, count: Int): Promise<List<GroupMessageInfo>, Exception> = sqlitePersistenceManager.runQuery { connection ->
        val tableName = GroupConversationTable.getTablename(groupId)
        val sql =
"""
SELECT
    id,
    speaker_contact_id,
    timestamp,
    received_timestamp,
    ttl,
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
                stmt.map { rowToGroupMessageInfo(it) }
            }
        }
        catch (e: SQLiteException) {
            if (isMissingGroupConvTableError(e))
                throw InvalidGroupException(groupId)
            else
                throw e
        }
    }

    override fun getUndeliveredMessages(): Promise<Map<GroupId, List<GroupMessageInfo>>, Exception> {
        TODO()
    }

    /* The following should only be used within tests to insert dummy data for testing purposes. */

    internal fun testSetConversationInfo(groupConversationInfo: GroupConversationInfo) = sqlitePersistenceManager.syncRunQuery {
        updateConversationInfo(
            it,
            groupConversationInfo.groupId,
            groupConversationInfo.lastSpeaker,
            groupConversationInfo.lastMessage,
            groupConversationInfo.lastTimestamp,
            0
            )
    }

    internal fun testAddGroupInfo(groupInfo: GroupInfo): Unit = sqlitePersistenceManager.syncRunQuery { connection ->
        insertOrReplaceGroupInfo(connection, groupInfo)
        insertOrReplaceNewGroupConversationInfo(connection, groupInfo.id)
        if (groupInfo.membershipLevel == GroupMembershipLevel.JOINED)
            createGroupConversationTable(connection, groupInfo.id)
    }

    internal fun testAddGroupMembers(id: GroupId, members: Set<UserId>): Unit = sqlitePersistenceManager.syncRunQuery { connection ->
        insertGroupMembers(connection, id, members)
    }

    internal fun testMessageExists(id: GroupId, messageId: String): Boolean = sqlitePersistenceManager.syncRunQuery { connection ->
        connection.withPrepared("SELECT 1 FROM ${GroupConversationTable.getTablename(id)} WHERE id=?") { stmt ->
            stmt.bind(1, messageId)
            stmt.step()
        }
    }

    internal fun testGetGroupConversationInfo(id: GroupId): GroupConversationInfo? = sqlitePersistenceManager.syncRunQuery { connection ->
        connection.withPrepared("SELECT last_speaker_contact_id, unread_count, last_message, last_timestamp FROM group_conversation_info WHERE group_id=?") { stmt ->
            stmt.bind(1, id)

            if (stmt.step())
                rowToGroupConversationInfo(stmt, id)
            else
                null
        }
    }

    fun testGetAllMessages(groupId: GroupId): List<GroupMessageInfo> = sqlitePersistenceManager.syncRunQuery { connection ->
        val tableName = GroupConversationTable.getTablename(groupId)
        val sql =
"""
SELECT
    id,
    speaker_contact_id,
    timestamp,
    received_timestamp,
    ttl,
    is_delivered,
    message
FROM
    $tableName
ORDER BY
    timestamp, n
"""
        connection.withPrepared(sql) { stmt ->
            stmt.map { rowToGroupMessageInfo(it) }
        }
    }

    fun testGetMessageInfo(groupId: GroupId, messageId: String): GroupMessageInfo? = sqlitePersistenceManager.syncRunQuery { connection ->
        getGroupMessageInfo(connection, groupId,  messageId)
    }
}