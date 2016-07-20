package io.slychat.messenger.core.persistence

import io.slychat.messenger.core.UserId
import nl.komponents.kovenant.Promise

interface GroupPersistenceManager {
    /** Returns the list of all currently joined groups. Does not include blocked or parted groups. */
    fun getGroupList(): Promise<List<GroupInfo>, Exception>

    /** Returns info on a specific group. */
    fun getGroupInfo(groupId: GroupId): Promise<GroupInfo?, Exception>

    /** Returns the membership list of the given group. If the group doesn't exist, an InvalidGroupException is thrown. */
    fun getGroupMembers(groupId: GroupId): Promise<Set<UserId>, Exception>

    /** Returns all group conversations. */
    fun getAllGroupConversationInfo(): Promise<List<GroupConversationInfo>, Exception>

    /** Add new members to the given group. The group entry must already exist. Returns the new set of members. */
    fun addMembers(groupId: GroupId, users: Set<UserId>): Promise<Set<UserId>, Exception>

    /** Remove a member from a group member list. If the user is not a member, does nothing and returns false. */
    fun removeMember(groupId: GroupId, userId: UserId): Promise<Boolean, Exception>

    /** Verifies if a given member is part of a joined group. */
    fun isUserMemberOf(userId: UserId, groupId: GroupId): Promise<Boolean, Exception>

    /** Create a new group with a set of possibly empty initial members. */
    fun createGroup(groupInfo: GroupInfo, initialMembers: Set<UserId>): Promise<Unit, Exception>

    /** Join a new group, or rejoin an existing group. */
    fun joinGroup(groupInfo: GroupInfo, members: Set<UserId>): Promise<Unit, Exception>

    /** Part a joined group. If not a member, returns false, otherwise returns true. */
    fun partGroup(groupId: GroupId): Promise<Boolean, Exception>

    /** Returns blocked groups. */
    fun getBlockList(): Promise<List<GroupId>, Exception>

    /** Block the given group. */
    fun blockGroup(groupId: GroupId): Promise<Unit, Exception>

    /** Unblock the given group. */
    fun unblockGroup(groupId: GroupId): Promise<Unit, Exception>

    /** Add a message from a user to the given group. If userId is null, is taken to be from yourself. */
    fun addMessage(groupId: GroupId, userId: UserId?, messageInfo: MessageInfo): Promise<MessageInfo, Exception>

    /** Adds a list of messages from a single user to the given group. */
    fun addMessages(groupId: GroupId, userId: UserId?, messages: Collection<MessageInfo>): Promise<List<MessageInfo>, Exception>

    /** Removes a set of messages from the group log. */
    fun deleteMessages(groupId: GroupId, messageIds: Collection<String>): Promise<Unit, Exception>

    /** Clears a group log. */
    fun deleteAllMessages(groupId: GroupId): Promise<Unit, Exception>

    /** Marks the given group message as delivered. */
    fun markMessageAsDelivered(groupId: GroupId, messageId: String): Promise<MessageInfo, Exception>

    /** Returns the last range of messages from a group. */
    fun getLastMessages(groupId: GroupId, startingAt: Int, count: Int): Promise<List<GroupMessageInfo>, Exception>

    /** Returns all undelivered messages for a given group. */
    fun getUndeliveredMessages(): Promise<Map<GroupId, List<GroupMessageInfo>>, Exception>
}