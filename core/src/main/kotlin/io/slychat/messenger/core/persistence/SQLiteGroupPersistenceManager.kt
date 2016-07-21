package io.slychat.messenger.core.persistence

import com.almworks.sqlite4java.SQLiteConnection
import com.almworks.sqlite4java.SQLiteStatement
import io.slychat.messenger.core.UserId
import io.slychat.messenger.core.persistence.sqlite.*
import nl.komponents.kovenant.Promise

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

    override fun addMembers(groupId: GroupId, users: Set<UserId>): Promise<Set<UserId>, Exception> {
        TODO()
    }

    override fun removeMember(groupId: GroupId, userId: UserId): Promise<Boolean, Exception> {
        TODO()
    }

    override fun isUserMemberOf(userId: UserId, groupId: GroupId): Promise<Boolean, Exception> {
        TODO()
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
                //rejoin
                if (maybeInfo != null)
                    clearMemberList(connection, groupInfo.id)

                insertOrReplaceGroupInfo(connection, groupInfo)
                createGroupConversationTable(connection, groupInfo.id)
                insertGroupMembers(connection, groupInfo.id, members)
            }
        }
    }

    private fun insertGroupMembers(connection: SQLiteConnection, id: GroupId, members: Set<UserId>) {
        connection.batchInsert("INSERT INTO group_members (group_id, contact_id) VALUES (?, ?)", members) { stmt, member ->
            stmt.bind(1, id)
            stmt.bind(2, member)
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

    override fun partGroup(groupId: GroupId): Promise<Boolean, Exception> {
        TODO()
    }

    override fun getBlockList(): Promise<List<GroupId>, Exception> {
        TODO()
    }

    fun isBlocked(groupId: GroupId): Promise<Boolean, Exception> {
        TODO()
    }

    override fun blockGroup(groupId: GroupId): Promise<Unit, Exception> {
        TODO()
    }

    override fun unblockGroup(groupId: GroupId): Promise<Unit, Exception> {
        TODO()
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