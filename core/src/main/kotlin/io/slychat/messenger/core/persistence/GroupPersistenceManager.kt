package io.slychat.messenger.core.persistence

import io.slychat.messenger.core.UserId
import nl.komponents.kovenant.Promise

/**
 * Manages groups.
 *
 * Only groups in JOINED state have member lists, conversation info and a conversation log.
 */
interface GroupPersistenceManager {
    /** Returns the list of all currently joined groups. Does not include blocked or parted groups. */
    fun getList(): Promise<List<GroupInfo>, Exception>

    /** Returns info on a specific group. */
    fun getInfo(groupId: GroupId): Promise<GroupInfo?, Exception>

    /** Returns the membership list of the given group. If the group doesn't exist, an InvalidGroupException is thrown. */
    fun getMembers(groupId: GroupId): Promise<Set<UserId>, Exception>

    /** Return conversation info for the specified group. */
    fun getConversationInfo(groupId: GroupId): Promise<GroupConversationInfo?, Exception>

    /** Returns group data + group conversation info. */
    fun getAllConversations(): Promise<List<GroupConversation>, Exception>

    /** Add new members to the given group. The group entry must already exist. Returns the new set of members. */
    fun addMembers(groupId: GroupId, users: Set<UserId>): Promise<Set<UserId>, Exception>

    /** Remove a member from a group member list. If the user is not a member, does nothing and returns false. */
    fun removeMember(groupId: GroupId, userId: UserId): Promise<Boolean, Exception>

    /** Verifies if a given member is part of a joined group. */
    fun isUserMemberOf(groupId: GroupId, userId: UserId): Promise<Boolean, Exception>

    /** Join a new group, or rejoin an existing group. Also used when creating a group yourself. Returns true if group was joined, false if group was already joined. */
    fun join(groupInfo: GroupInfo, members: Set<UserId>): Promise<Boolean, Exception>

    /** Part a joined group. If not a member, returns false, otherwise returns true. */
    fun part(groupId: GroupId): Promise<Boolean, Exception>

    /** Returns blocked groups. */
    fun getBlockList(): Promise<Set<GroupId>, Exception>

    /** Block the given group. */
    fun block(groupId: GroupId): Promise<Boolean, Exception>

    /** Unblock the given group. */
    fun unblock(groupId: GroupId): Promise<Boolean, Exception>

    /** Add a message from a user to the given group. If userId is null, is taken to be from yourself. */
    fun addMessage(groupId: GroupId, groupMessageInfo: GroupMessageInfo): Promise<GroupMessageInfo, Exception>

    /** Removes a set of messages from the group log. */
    fun deleteMessages(groupId: GroupId, messageIds: Collection<String>): Promise<Unit, Exception>

    /** Clears a group log. */
    fun deleteAllMessages(groupId: GroupId): Promise<Unit, Exception>

    /** Marks the given group message as delivered. */
    fun markMessageAsDelivered(groupId: GroupId, messageId: String): Promise<GroupMessageInfo, Exception>

    /** Resets unread message count for the given contact's conversation. */
    fun markConversationAsRead(groupId: GroupId): Promise<Unit, Exception>

    /** Returns the last range of messages from a group. */
    fun getLastMessages(groupId: GroupId, startingAt: Int, count: Int): Promise<List<GroupMessageInfo>, Exception>

    /** Returns all undelivered messages for a given group. */
    fun getUndeliveredMessages(): Promise<Map<GroupId, List<GroupMessageInfo>>, Exception>

    fun applyDiff(updates: Collection<AddressBookUpdate.Group>): Promise<Unit, Exception>

    fun getRemoteUpdates(): Promise<List<AddressBookUpdate.Group>, Exception>
    fun removeRemoteUpdates(remoteUpdates: Collection<GroupId>): Promise<Unit, Exception>
}