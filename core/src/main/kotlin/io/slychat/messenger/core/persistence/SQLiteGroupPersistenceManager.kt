package io.slychat.messenger.core.persistence

import io.slychat.messenger.core.UserId
import io.slychat.messenger.core.persistence.sqlite.SQLitePersistenceManager
import nl.komponents.kovenant.Promise

class SQLiteGroupPersistenceManager(
    private val sqlitePersistenceManager: SQLitePersistenceManager
) : GroupPersistenceManager {
    override fun getGroupList(): Promise<List<GroupInfo>, Exception> {
        TODO()
    }

    override fun getGroupInfo(groupId: GroupId): Promise<GroupInfo?, Exception> {
        TODO()
    }

    override fun getGroupMembers(groupId: GroupId): Promise<Set<UserId>, Exception> {
        TODO()
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

    override fun joinGroup(groupInfo: GroupInfo, members: Set<UserId>): Promise<Unit, Exception> {
        TODO()
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
}