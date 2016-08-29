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
    private fun GroupMembershipLevel.toInt(): Int = when (this) {
        GroupMembershipLevel.BLOCKED -> 0
        GroupMembershipLevel.PARTED -> 1
        GroupMembershipLevel.JOINED -> 2
    }

    private fun Int.toGroupMembershipLevel(): GroupMembershipLevel = when (this) {
        0 -> GroupMembershipLevel.BLOCKED
        1 -> GroupMembershipLevel.PARTED
        2 -> GroupMembershipLevel.JOINED
        else -> throw IllegalArgumentException("Invalid integer value for MembershipLevel: $this")
    }

    override fun getList(): Promise<List<GroupInfo>, Exception> = sqlitePersistenceManager.runQuery { connection ->
        connection.withPrepared("SELECT id, name, membership_level FROM groups WHERE membership_level=?") { stmt ->
            stmt.bind(1, GroupMembershipLevel.JOINED.toInt())
            stmt.map { rowToGroupInfo(stmt) }
        }
    }

    override fun getInfo(groupId: GroupId): Promise<GroupInfo?, Exception> = sqlitePersistenceManager.runQuery { connection ->
        queryGroupInfo(connection, groupId)
    }

    override fun getMembers(groupId: GroupId): Promise<Set<UserId>, Exception> = sqlitePersistenceManager.runQuery { connection ->
        throwIfGroupIsInvalid(connection, groupId)
        queryGroupMembers(connection, groupId)
    }

    override fun getNonBlockedMembers(groupId: GroupId): Promise<Set<UserId>, Exception> = sqlitePersistenceManager.runQuery { connection ->
        throwIfGroupIsInvalid(connection, groupId)
        queryNonBlockedGroupMembers(connection, groupId)
    }

    private fun queryGroupConversationInfo(connection: SQLiteConnection, groupId: GroupId): GroupConversationInfo? {
        return connection.withPrepared("SELECT last_speaker_contact_id, unread_count, last_message, last_timestamp FROM group_conversation_info WHERE group_id=?") { stmt ->
            stmt.bind(1, groupId)

            if (stmt.step())
                rowToGroupConversationInfo(stmt, groupId)
            else
                null
        }
    }

    override fun getConversationInfo(groupId: GroupId): Promise<GroupConversationInfo?, Exception> = sqlitePersistenceManager.runQuery { connection ->
        val info = queryGroupInfo(connection, groupId)
        if (info == null)
            throw InvalidGroupException(groupId)
        else
            queryGroupConversationInfo(connection, groupId)
    }

    override fun getAllConversations(): Promise<List<GroupConversation>, Exception> = sqlitePersistenceManager.runQuery { connection ->
        val sql =
"""
SELECT
    c.last_speaker_contact_id,
    c.unread_count,
    c.last_message,
    c.last_timestamp,
    g.id,
    g.name,
    g.membership_level
FROM
    group_conversation_info
AS
    c
JOIN
    groups
AS
    g
ON
    c.group_id=g.id
WHERE
    g.membership_level=?
"""
        connection.withPrepared(sql) { stmt ->
            stmt.bind(1, GroupMembershipLevel.JOINED.toInt())
            stmt.map {
                val groupInfo = rowToGroupInfo(stmt, 4)
                val convoInfo = rowToGroupConversationInfo(it, groupInfo.id)

                GroupConversation(groupInfo, convoInfo)
            }
        }
    }

    override fun addMembers(groupId: GroupId, users: Set<UserId>): Promise<Set<UserId>, Exception> {
        return if (users.isNotEmpty())
            sqlitePersistenceManager.runQuery { connection ->
                val currentMembers = queryGroupMembers(connection, groupId)

                val newMembers = HashSet(users)
                newMembers.removeAll(currentMembers)

                if (newMembers.isNotEmpty()) {
                    insertGroupMembers(connection, groupId, newMembers)
                    insertOrReplaceRemoteUpdate(connection, groupId)
                }

                newMembers
            }
        else
            Promise.ofSuccess(emptySet())
    }

    override fun removeMember(groupId: GroupId, userId: UserId): Promise<Boolean, Exception> = sqlitePersistenceManager.runQuery { connection ->
        throwIfGroupIsInvalid(connection, groupId)

        connection.withPrepared("DELETE FROM group_members WHERE group_id=? AND contact_id=?") { stmt ->
            stmt.bind(1, groupId)
            stmt.bind(2, userId)

            stmt.step()

        }

        val wasRemoved = connection.changes > 0

        if (wasRemoved)
            insertOrReplaceRemoteUpdate(connection, groupId)

        wasRemoved
    }

    private fun throwIfGroupIsInvalid(connection: SQLiteConnection, groupId: GroupId) {
        val exists = connection.withPrepared("SELECT 1 FROM groups WHERE id=?") { stmt ->
            stmt.bind(1, groupId)
            stmt.step()
        }

        if (!exists)
            throw InvalidGroupException(groupId)
    }

    override fun isUserMemberOf(groupId: GroupId, userId: UserId): Promise<Boolean, Exception> = sqlitePersistenceManager.runQuery { connection ->
        throwIfGroupIsInvalid(connection, groupId)

        connection.withPrepared("SELECT 1 FROM group_members WHERE group_id=? AND contact_id=?") { stmt ->
            stmt.bind(1, groupId)
            stmt.bind(2, userId)
            stmt.step()
        }
    }

    override fun join(groupInfo: GroupInfo, members: Set<UserId>): Promise<Boolean, Exception> = sqlitePersistenceManager.runQuery { connection ->
        require(groupInfo.membershipLevel == GroupMembershipLevel.JOINED) { "Invalid membershipLevel: ${groupInfo.membershipLevel}"}

        val groupId = groupInfo.id
        val maybeInfo = queryGroupInfo(connection, groupId)

        //do nothing if we're already joined
        if (maybeInfo != null && maybeInfo.membershipLevel == GroupMembershipLevel.JOINED) {
            false
        }
        else {
            connection.withTransaction {
                //rejoin (this should already be empty anyways from parting/blocking)
                if (maybeInfo != null)
                    clearMemberList(connection, groupId)

                insertOrReplaceGroupInfo(connection, groupInfo)
                createConversationData(connection, groupInfo.id)
                insertGroupMembers(connection, groupId, members)

                insertOrReplaceRemoteUpdate(connection, groupId)
            }

            true
        }
    }

    private fun deleteGroupConversationInfo(connection: SQLiteConnection, id: GroupId) {
        connection.withPrepared("DELETE FROM group_conversation_info WHERE group_id=?") { stmt ->
            stmt.bind(1, id)
            stmt.step()
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

    private fun insertOrReplaceGroupInfo(connection: SQLiteConnection, groupInfo: GroupInfo) {
        connection.withPrepared("INSERT OR REPLACE INTO groups (id, name, membership_level) VALUES (?, ?, ?)") { stmt ->
            stmt.bind(1, groupInfo.id)
            stmt.bind(2, groupInfo.name)
            stmt.bind(3, groupInfo.membershipLevel.toInt())
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

    private fun queryNonBlockedGroupMembers(connection: SQLiteConnection, id: GroupId): Set<UserId> {
        val sql = """
SELECT
    contact_id
FROM
    group_members
JOIN
    contacts
ON
    contacts.id = contact_id
WHERE
    group_id = ?
AND
    contacts.allowed_message_level != ?
"""

        return connection.withPrepared(sql) { stmt ->
            stmt.bind(1, id)
            stmt.bind(2, AllowedMessageLevel.BLOCKED)

            stmt.mapToSet { UserId(it.columnLong(0)) }
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

    private fun rowToGroupInfo(stmt: SQLiteStatement, startIndex: Int = 0): GroupInfo {
        return GroupInfo(
            GroupId(stmt.columnString(startIndex)),
            stmt.columnString(startIndex+1),
            stmt.columnInt(startIndex+2).toGroupMembershipLevel()
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
            stmt.bind(1, membershipLevel.toInt())
            stmt.bind(2, groupId)
            stmt.step()
        }
    }

    private fun createConversationData(connection: SQLiteConnection, groupId: GroupId) {
        insertOrReplaceNewGroupConversationInfo(connection, groupId)
        GroupConversationTable.create(connection, groupId)
    }

    private fun deleteConversationData(connection: SQLiteConnection, groupId: GroupId) {
        GroupConversationTable.delete(connection, groupId)
        deleteGroupConversationInfo(connection, groupId)
    }

    //parted -> joined
    private fun doJoinGroup(connection: SQLiteConnection, groupId: GroupId) {
        updateMembershipLevel(connection, groupId, GroupMembershipLevel.JOINED)
        createConversationData(connection, groupId)
    }

    //joined -> parted
    private fun doPartGroup(connection: SQLiteConnection, groupId: GroupId) {
        clearMemberList(connection, groupId)

        updateMembershipLevel(connection, groupId, GroupMembershipLevel.PARTED)
        deleteConversationData(connection, groupId)
    }

    //joined -> blocked
    private fun doBlockGroup(connection: SQLiteConnection, groupId: GroupId) {
        clearMemberList(connection, groupId)
        updateMembershipLevel(connection, groupId, GroupMembershipLevel.BLOCKED)
        deleteConversationData(connection, groupId)
    }

    //blocked -> parted
    private fun doUnblockGroup(connection: SQLiteConnection, groupId: GroupId) {
        updateMembershipLevel(connection, groupId, GroupMembershipLevel.PARTED)
    }

    //clear the member list and set it to the given group
    private fun setGroupMembersTo(connection: SQLiteConnection, groupId: GroupId, members: Set<UserId>) {
        clearMemberList(connection, groupId)
        insertGroupMembers(connection, groupId, members)
    }

    //used by applyDiff
    private fun doGroupTransition(connection: SQLiteConnection, groupId: GroupId, oldMembershipLevel: GroupMembershipLevel, newMembershipLevel: GroupMembershipLevel) {
        if (oldMembershipLevel == newMembershipLevel)
            return

        when (oldMembershipLevel) {
            GroupMembershipLevel.JOINED -> when (newMembershipLevel) {
                GroupMembershipLevel.PARTED ->
                    doPartGroup(connection, groupId)

                GroupMembershipLevel.BLOCKED ->
                    doBlockGroup(connection, groupId)

                GroupMembershipLevel.JOINED -> error("Can't happen")
            }

            GroupMembershipLevel.PARTED -> when (newMembershipLevel) {
                GroupMembershipLevel.JOINED ->
                    doJoinGroup(connection, groupId)

                GroupMembershipLevel.BLOCKED ->
                    doBlockGroup(connection, groupId)

                GroupMembershipLevel.PARTED -> error("Can't happen")
            }

            GroupMembershipLevel.BLOCKED -> when (newMembershipLevel) {
                GroupMembershipLevel.JOINED ->
                    doJoinGroup(connection, groupId)

                GroupMembershipLevel.PARTED ->
                    doPartGroup(connection, groupId)

                GroupMembershipLevel.BLOCKED -> error("Can't happen")
            }
        }
    }

    override fun part(groupId: GroupId): Promise<Boolean, Exception> = sqlitePersistenceManager.runQuery { connection ->
        val groupInfo = queryGroupInfoOrThrow(connection, groupId)

        when (groupInfo.membershipLevel) {
            GroupMembershipLevel.PARTED -> false

            GroupMembershipLevel.BLOCKED -> false

            GroupMembershipLevel.JOINED -> {
                connection.withTransaction {
                    doPartGroup(connection, groupId)
                    insertOrReplaceRemoteUpdate(connection, groupId)
                }

                true
            }
        }
    }

    override fun getBlockList(): Promise<Set<GroupId>, Exception> = sqlitePersistenceManager.runQuery { connection ->
        connection.withPrepared("SELECT id FROM groups WHERE membership_level=?") { stmt ->
            stmt.bind(1, GroupMembershipLevel.BLOCKED.toInt())

            stmt.mapToSet { GroupId(it.columnString(0)) }
        }
    }

    override fun block(groupId: GroupId): Promise<Boolean, Exception> = sqlitePersistenceManager.runQuery { connection ->
        val groupInfo = queryGroupInfoOrThrow(connection, groupId)

        if (groupInfo.membershipLevel == GroupMembershipLevel.BLOCKED)
            false
        else {
            connection.withTransaction {
                doBlockGroup(connection, groupId)
                insertOrReplaceRemoteUpdate(connection, groupId)
            }

            true
        }
    }

    override fun unblock(groupId: GroupId): Promise<Boolean, Exception> = sqlitePersistenceManager.runQuery { connection ->
        val groupInfo = queryGroupInfoOrThrow(connection, groupId)

        when (groupInfo.membershipLevel) {
            GroupMembershipLevel.JOINED -> false
            GroupMembershipLevel.PARTED -> false
            GroupMembershipLevel.BLOCKED -> {
                connection.withTransaction {
                    doUnblockGroup(connection, groupId)
                    insertOrReplaceRemoteUpdate(connection, groupId)
                }
                true
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

    /** Throws InvalidGroupException if group_conv table was missing, else rethrows the given exception. */
    private fun handleInvalidGroupException(e: SQLiteException, groupId: GroupId): Nothing {
        if (isMissingGroupConvTableError(e))
            throw InvalidGroupException(groupId)
        else
            throw e
    }

    override fun deleteMessages(groupId: GroupId, messageIds: Collection<String>): Promise<Unit, Exception> = sqlitePersistenceManager.runQuery { connection ->
        if (messageIds.isNotEmpty()) {
            val tableName = GroupConversationTable.getTablename(groupId)

            try {
                connection.prepare("DELETE FROM $tableName WHERE id IN (${getPlaceholders(messageIds.size)})").use { stmt ->
                    messageIds.forEachIndexed { i, messageId ->
                        stmt.bind(i + 1, messageId)
                    }

                    stmt.step()
                }
            }
            catch (e: SQLiteException) {
                handleInvalidGroupException(e, groupId)
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

    override fun markMessageAsDelivered(groupId: GroupId, messageId: String): Promise<GroupMessageInfo?, Exception> = sqlitePersistenceManager.runQuery { connection ->
        val tableName = GroupConversationTable.getTablename(groupId)

        val sql = "UPDATE $tableName SET is_delivered=1, received_timestamp=? WHERE id=?"

        val currentInfo = try {
            getGroupMessageInfo(connection, groupId, messageId) ?: throw InvalidGroupMessageException(groupId, messageId)
        }
        catch (e: SQLiteException) {
            handleInvalidGroupException(e, groupId)
        }

        if (!currentInfo.info.isDelivered) {
            try {
                connection.withPrepared(sql) { stmt ->
                    stmt.bind(1, currentTimestamp())
                    stmt.bind(2, messageId)
                    stmt.step()
                }
            }
            catch (e: SQLiteException) {
                handleInvalidGroupException(e, groupId)
            }

            if (connection.changes <= 0)
                throw InvalidGroupMessageException(groupId, messageId)

            getGroupMessageInfo(connection, groupId, messageId) ?: throw InvalidGroupMessageException(groupId, messageId)
        }
        else
            null
    }

    override fun markConversationAsRead(groupId: GroupId): Promise<Unit, Exception> = sqlitePersistenceManager.runQuery { connection ->
        throwIfGroupIsInvalid(connection, groupId)

        connection.withPrepared("UPDATE group_conversation_info set unread_count=0 WHERE group_id=?") { stmt ->
            stmt.bind(1, groupId)
            stmt.step()
        }

        Unit
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
            handleInvalidGroupException(e, groupId)
        }
    }

    override fun getUndeliveredMessages(): Promise<Map<GroupId, List<GroupMessageInfo>>, Exception> {
        TODO()
    }

    private fun insertOrReplaceRemoteUpdate(connection: SQLiteConnection, groupId: GroupId) {
        connection.withPrepared("INSERT OR REPLACE INTO remote_group_updates (group_id) VALUES (?)") { stmt ->
            stmt.bind(1, groupId)
            stmt.step()
        }
    }

    private fun applyDiff(connection: SQLiteConnection, update: AddressBookUpdate.Group) {
        val groupId = update.groupId
        val maybeInfo = queryGroupInfo(connection, groupId)

        //new group, create it
        if (maybeInfo == null) {
            val newGroupInfo = GroupInfo(groupId, update.name, update.membershipLevel)
            insertOrReplaceGroupInfo(connection, newGroupInfo)

            if (update.membershipLevel == GroupMembershipLevel.JOINED)
                createConversationData(connection, groupId)
        }
        //existing group, transition
        else
            doGroupTransition(connection, groupId, maybeInfo.membershipLevel, update.membershipLevel)

        if (update.members.isNotEmpty())
            setGroupMembersTo(connection, groupId, update.members)
    }

    override fun applyDiff(updates: Collection<AddressBookUpdate.Group>): Promise<Unit, Exception> {
        return if (updates.isEmpty())
            Promise.ofSuccess(Unit)
        else sqlitePersistenceManager.runQuery { connection ->
            connection.withTransaction {
                updates.forEach { update ->
                    applyDiff(connection, update)
                }
            }
        }
    }

    override fun getRemoteUpdates(): Promise<List<AddressBookUpdate.Group>, Exception> = sqlitePersistenceManager.runQuery { connection ->
        val sql = """
SELECT
    g.id,
    g.name,
    g.membership_level
FROM
    remote_group_updates AS r
JOIN
    groups AS g
ON
    r.group_id = g.id
"""
        val groups = connection.withPrepared(sql) { stmt ->
            stmt.map { rowToGroupInfo(stmt) }
        }

        groups.map {
            val members = queryGroupMembers(connection, it.id)
            AddressBookUpdate.Group(it.id, it.name,  members, it.membershipLevel)
        }
    }

    override fun removeRemoteUpdates(remoteUpdates: Collection<GroupId>): Promise<Unit, Exception> {
        return if (remoteUpdates.isNotEmpty())
            sqlitePersistenceManager.runQuery { connection ->
                connection.withTransaction {
                    connection.withPrepared("DELETE FROM remote_group_updates WHERE group_id=?") { stmt ->
                        remoteUpdates.forEach {
                            stmt.bind(1, it)
                            stmt.step()
                            stmt.reset()
                        }
                    }
                }
            }
        else
            Promise.ofSuccess(Unit)
    }

    /* The following should only be used within tests to insert dummy data for testing purposes. */

    internal fun internalSetConversationInfo(groupConversationInfo: GroupConversationInfo) = sqlitePersistenceManager.syncRunQuery {
        updateConversationInfo(
            it,
            groupConversationInfo.groupId,
            groupConversationInfo.lastSpeaker,
            groupConversationInfo.lastMessage,
            groupConversationInfo.lastTimestamp,
            groupConversationInfo.unreadCount
        )
    }

    internal fun internalAddInfo(groupInfo: GroupInfo): Unit = sqlitePersistenceManager.syncRunQuery { connection ->
        connection.withTransaction {
            insertOrReplaceGroupInfo(connection, groupInfo)
            if (groupInfo.membershipLevel == GroupMembershipLevel.JOINED) {
                createConversationData(connection, groupInfo.id)
            }
        }
    }

    internal fun internalAddMembers(id: GroupId, members: Set<UserId>): Unit {
        if (members.isNotEmpty())
            sqlitePersistenceManager.syncRunQuery { connection ->
                insertGroupMembers(connection, id, members)
            }
    }

    internal fun internalMessageExists(id: GroupId, messageId: String): Boolean = sqlitePersistenceManager.syncRunQuery { connection ->
        connection.withPrepared("SELECT 1 FROM ${GroupConversationTable.getTablename(id)} WHERE id=?") { stmt ->
            stmt.bind(1, messageId)
            stmt.step()
        }
    }

    internal fun internalGetConversationInfo(id: GroupId): GroupConversationInfo? = sqlitePersistenceManager.syncRunQuery { connection ->
        queryGroupConversationInfo(connection, id)
    }

    internal fun internalGetAllMessages(groupId: GroupId): List<GroupMessageInfo> = sqlitePersistenceManager.syncRunQuery { connection ->
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

    internal fun internalGetMessageInfo(groupId: GroupId, messageId: String): GroupMessageInfo? = sqlitePersistenceManager.syncRunQuery { connection ->
        getGroupMessageInfo(connection, groupId,  messageId)
    }

    internal fun internalAddRemoteUpdates(groups: Set<GroupId>) = sqlitePersistenceManager.syncRunQuery {
        it.batchInsert("INSERT INTO remote_group_updates (group_id) VALUES (?)", groups) { stmt, item ->
            stmt.bind(1, item)
        }
    }

    internal fun internalClearRemoteUpdates() = sqlitePersistenceManager.syncRunQuery {
        it.withPrepared("DELETE FROM remote_group_updates") { it.step() }
    }
}