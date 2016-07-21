package io.slychat.messenger.core.persistence

import com.almworks.sqlite4java.SQLiteConnection
import com.almworks.sqlite4java.SQLiteException
import com.almworks.sqlite4java.SQLiteStatement
import io.slychat.messenger.core.UserId
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
        connection.withPrepared("DELETE FROM group_members WHERE group_id=? AND contact_id=?") { stmt ->
            stmt.bind(1, groupId)
            stmt.bind(2, userId)

            stmt.step()

        }

        connection.changes > 0
    }

    override fun isUserMemberOf(groupId: GroupId, userId: UserId): Promise<Boolean, Exception> = sqlitePersistenceManager.runQuery { connection ->
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
                createGroupConversationTable(connection, groupInfo.id)
                insertGroupMembers(connection, groupInfo.id, members)
            }
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

    override fun addMessage(groupId: GroupId, userId: UserId?, messageInfo: MessageInfo): Promise<MessageInfo, Exception> {
        TODO()
    }

    override fun addMessages(groupId: GroupId, userId: UserId?, messages: Collection<MessageInfo>): Promise<List<MessageInfo>, Exception> {
        TODO()
    }

    override fun deleteMessages(groupId: GroupId, messageIds: Collection<String>): Promise<Unit, Exception> {
        TODO()
    }

    override fun deleteAllMessages(groupId: GroupId): Promise<Unit, Exception> {
        TODO()
    }

    override fun markMessageAsDelivered(groupId: GroupId, messageId: String): Promise<MessageInfo, Exception> {
        TODO()
    }

    override fun getLastMessages(groupId: GroupId, startingAt: Int, count: Int): Promise<List<GroupMessageInfo>, Exception> {
        TODO()
    }

    override fun getUndeliveredMessages(): Promise<Map<GroupId, List<GroupMessageInfo>>, Exception> {
        TODO()
    }

    /* The following should only be used within tests to insert dummy data for testing purposes. */

    internal fun testAddGroupInfo(groupInfo: GroupInfo): Unit = sqlitePersistenceManager.syncRunQuery { connection ->
        insertOrReplaceGroupInfo(connection, groupInfo)
    }

    internal fun testAddGroupMembers(id: GroupId, members: Set<UserId>): Unit = sqlitePersistenceManager.syncRunQuery { connection ->
        insertGroupMembers(connection, id, members)
    }
}