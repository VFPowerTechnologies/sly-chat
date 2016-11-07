package io.slychat.messenger.core.persistence.sqlite

import com.almworks.sqlite4java.SQLiteConnection
import com.almworks.sqlite4java.SQLiteException
import com.almworks.sqlite4java.SQLiteStatement
import io.slychat.messenger.core.UserId
import io.slychat.messenger.core.persistence.*
import nl.komponents.kovenant.Promise
import java.util.*

//FIXME
internal fun insertOrReplaceNewConversationInfo(connection: SQLiteConnection, id: ConversationId) {
    val sql =
        """
INSERT OR REPLACE INTO conversation_info
    (conversation_id, last_speaker_contact_id, unread_count, last_message, last_timestamp)
VALUES
    (?, null, 0, null, null)
"""
    connection.withPrepared(sql) { stmt ->
        stmt.bind(1, id)
        stmt.step()
    }
}

internal fun rowToGroupInfo(stmt: SQLiteStatement, startIndex: Int = 0): GroupInfo {
    return GroupInfo(
        GroupId(stmt.columnString(startIndex)),
        stmt.columnString(startIndex+1),
        stmt.columnGroupMembershipLevel(startIndex+2)
    )
}

internal fun queryGroupInfo(connection: SQLiteConnection, id: GroupId): GroupInfo? {
    return connection.withPrepared("SELECT id, name, membership_level FROM groups WHERE id=?") { stmt ->
        stmt.bind(1, id)

        if (stmt.step())
            rowToGroupInfo(stmt)
        else
            null
    }
}

class SQLiteGroupPersistenceManager(
    private val sqlitePersistenceManager: SQLitePersistenceManager
) : GroupPersistenceManager {
    private fun throwIfGroupIsInvalid(connection: SQLiteConnection, groupId: GroupId) {
        val exists = connection.withPrepared("SELECT 1 FROM groups WHERE id=?") { stmt ->
            stmt.bind(1, groupId)
            stmt.step()
        }

        if (!exists)
            throw InvalidGroupException(groupId)
    }

    override fun getList(): Promise<List<GroupInfo>, Exception> = sqlitePersistenceManager.runQuery { connection ->
        connection.withPrepared("SELECT id, name, membership_level FROM groups WHERE membership_level=?") { stmt ->
            stmt.bind(1, GroupMembershipLevel.JOINED)
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

    private fun deleteGroupConversationInfo(connection: SQLiteConnection, id: ConversationId) {
        connection.withPrepared("DELETE FROM conversation_info WHERE conversation_id=?") { stmt ->
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
            stmt.bind(3, groupInfo.membershipLevel)
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

    private fun updateMembershipLevel(connection: SQLiteConnection, groupId: GroupId, membershipLevel: GroupMembershipLevel) {
        connection.withPrepared("UPDATE groups set membership_level=? WHERE id=?") { stmt ->
            stmt.bind(1, membershipLevel)
            stmt.bind(2, groupId)
            stmt.step()
        }
    }

    private fun createConversationData(connection: SQLiteConnection, groupId: GroupId) {
        val id = ConversationId.Group(groupId)
        insertOrReplaceNewConversationInfo(connection, id)
        ConversationTable.create(connection, id)
    }

    private fun deleteConversationData(connection: SQLiteConnection, groupId: GroupId) {
        val id = ConversationId.Group(groupId)
        ConversationTable.delete(connection, id)
        deleteGroupConversationInfo(connection, id)
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
        deleteExpiringMessagesForConversation(connection, groupId.toConversationId())
    }

    //joined -> blocked
    private fun doBlockGroup(connection: SQLiteConnection, groupId: GroupId) {
        clearMemberList(connection, groupId)
        updateMembershipLevel(connection, groupId, GroupMembershipLevel.BLOCKED)
        deleteConversationData(connection, groupId)
        deleteExpiringMessagesForConversation(connection, groupId.toConversationId())
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
    private fun doGroupTransition(connection: SQLiteConnection, groupId: GroupId, oldMembershipLevel: GroupMembershipLevel, newMembershipLevel: GroupMembershipLevel): Boolean {
        if (oldMembershipLevel == newMembershipLevel)
            return false

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

        return true
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
            stmt.bind(1, GroupMembershipLevel.BLOCKED)

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

    private fun insertOrReplaceRemoteUpdate(connection: SQLiteConnection, groupId: GroupId) {
        connection.withPrepared("INSERT OR REPLACE INTO remote_group_updates (group_id) VALUES (?)") { stmt ->
            stmt.bind(1, groupId)
            stmt.step()
        }
    }

    private fun getDiffDelta(
        groupId: GroupId,
        name: String,
        membershipLevel: GroupMembershipLevel,
        oldMembers: Set<UserId>,
        currentMembers: Set<UserId>,
        transitionOccured: Boolean
    ): GroupDiffDelta? {
        //if we transitioned to a new state, just emit the corresponding delta
        return if (transitionOccured) {
            val transitionDelta = when (membershipLevel) {
                GroupMembershipLevel.BLOCKED -> GroupDiffDelta.Blocked(groupId)
                GroupMembershipLevel.PARTED -> GroupDiffDelta.Parted(groupId)
                GroupMembershipLevel.JOINED -> GroupDiffDelta.Joined(groupId, name, currentMembers)
            }

            transitionDelta
        }
        //else if we've stayed in the same state, then just emit member diffs
        else {
            val newMembers = currentMembers - oldMembers
            val partedMembers = oldMembers - currentMembers

            if (newMembers.isNotEmpty() || partedMembers.isNotEmpty())
                GroupDiffDelta.MembershipChanged(groupId, newMembers, partedMembers)
            else
                null
        }
    }

    private fun applyDiff(connection: SQLiteConnection, update: AddressBookUpdate.Group): GroupDiffDelta? {
        val groupId = update.groupId
        val maybeInfo = queryGroupInfo(connection, groupId)

        val transitionOccured = if (maybeInfo == null) {
            val newGroupInfo = GroupInfo(groupId, update.name, update.membershipLevel)
            insertOrReplaceGroupInfo(connection, newGroupInfo)

            if (update.membershipLevel == GroupMembershipLevel.JOINED)
                createConversationData(connection, groupId)

            true
        }
        //existing group, transition
        else
            doGroupTransition(connection, groupId, maybeInfo.membershipLevel, update.membershipLevel)

        //don't need member diffs for part/blocked since those have no members
        val wantMemberDiffs = !transitionOccured && update.membershipLevel == GroupMembershipLevel.JOINED
        val hasMembers = update.members.isNotEmpty()

        val oldMembers = if (wantMemberDiffs && hasMembers)
            queryGroupMembers(connection, groupId)
        else
            emptySet<UserId>()

        //member lists are cleared for parts/blocks already
        if (hasMembers)
            setGroupMembersTo(connection, groupId, update.members)

        return getDiffDelta(groupId, update.name, update.membershipLevel, oldMembers, update.members, transitionOccured)
    }

    override fun applyDiff(updates: Collection<AddressBookUpdate.Group>): Promise<List<GroupDiffDelta>, Exception> {
        return if (updates.isEmpty())
            Promise.ofSuccess(emptyList())
        else sqlitePersistenceManager.runQuery { connection ->
            val deltas = ArrayList<GroupDiffDelta>()

            connection.withTransaction {
                updates.forEach { update ->
                    val delta = applyDiff(connection, update)
                    if (delta != null)
                        deltas.add(delta)
                }
            }

            deltas
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
            AddressBookUpdate.Group(it.id, it.name, members, it.membershipLevel)
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

    internal fun internalAddRemoteUpdates(groups: Set<GroupId>) = sqlitePersistenceManager.syncRunQuery {
        it.batchInsert("INSERT INTO remote_group_updates (group_id) VALUES (?)", groups) { stmt, item ->
            stmt.bind(1, item)
        }
    }
}