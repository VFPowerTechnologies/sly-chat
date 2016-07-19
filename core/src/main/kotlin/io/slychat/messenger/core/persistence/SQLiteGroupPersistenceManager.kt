package io.slychat.messenger.core.persistence

import io.slychat.messenger.core.UserId
import io.slychat.messenger.core.persistence.sqlite.SQLitePersistenceManager
import nl.komponents.kovenant.Promise

class SQLiteGroupPersistenceManager(
    private val sqlitePersistenceManager: SQLitePersistenceManager
) : GroupPersistenceManager {
    override fun getGroupList(): Promise<List<GroupInfo>, Exception> {
        throw NotImplementedError()
    }

    override fun getGroupInfo(groupId: GroupId): Promise<GroupInfo?, Exception> {
        throw NotImplementedError()
    }

    override fun getGroupMembers(groupId: GroupId): Promise<Set<UserId>, Exception> {
        throw NotImplementedError()
    }

    override fun getAllGroupConversationInfo(): Promise<List<GroupConversationInfo>, Exception> {
        throw NotImplementedError()
    }

    override fun addMember(groupId: GroupId, userId: UserId): Promise<Boolean, Exception> {
        throw NotImplementedError()
    }

    override fun removeMember(groupId: GroupId, userId: UserId): Promise<Boolean, Exception> {
        throw NotImplementedError()
    }

    override fun isUserMemberOf(userId: UserId, groupId: GroupId): Promise<Boolean, Exception> {
        throw NotImplementedError()
    }

    override fun createGroup(groupInfo: GroupInfo, initialMembers: Set<UserId>): Promise<Unit, Exception> {
        throw NotImplementedError()
    }

    override fun joinGroup(groupInfo: GroupInfo, members: Set<UserId>): Promise<Unit, Exception> {
        throw NotImplementedError()
    }

    override fun partGroup(groupId: GroupId): Promise<Boolean, Exception> {
        throw NotImplementedError()
    }

    override fun getBlockList(): Promise<List<GroupId>, Exception> {
        throw NotImplementedError()
    }

    override fun isBlocked(groupId: GroupId): Promise<Boolean, Exception> {
        throw NotImplementedError()
    }

    override fun addMessage(groupId: GroupId, userId: UserId?, messageInfo: MessageInfo): Promise<MessageInfo, Exception> {
        throw NotImplementedError()
    }

    override fun addMessages(groupId: GroupId, userId: UserId?, messages: Collection<MessageInfo>): Promise<List<MessageInfo>, Exception> {
        throw NotImplementedError()
    }

    override fun deleteMessages(groupId: GroupId, messageIds: Collection<String>): Promise<Unit, Exception> {
        throw NotImplementedError()
    }

    override fun deleteAllMessages(groupId: GroupId): Promise<Unit, Exception> {
        throw NotImplementedError()
    }

    override fun markMessageAsDelivered(groupId: GroupId, messageId: String): Promise<MessageInfo, Exception> {
        throw NotImplementedError()
    }

    override fun getLastMessages(groupId: GroupId, startingAt: Int, count: Int): Promise<List<GroupMessageInfo>, Exception> {
        throw NotImplementedError()
    }

    override fun getUndeliveredMessages(): Promise<Map<GroupId, List<GroupMessageInfo>>, Exception> {
        throw NotImplementedError()
    }
}