package io.slychat.messenger.services

import io.slychat.messenger.core.UserId
import io.slychat.messenger.core.persistence.*
import io.slychat.messenger.services.messaging.GroupEvent
import nl.komponents.kovenant.Promise
import rx.Observable

interface GroupService {
    val groupEvents: Observable<GroupEvent>

    fun addMembers(groupId: GroupId, users: Set<UserId>): Promise<Unit, Exception>

    fun removeMember(groupId: GroupId, userId: UserId): Promise<Unit, Exception>

    fun getMembers(groupId: GroupId): Promise<Set<UserId>, Exception>

    fun getInfo(groupId: GroupId): Promise<GroupInfo?, Exception>

    fun addMessage(groupId: GroupId, groupMessageInfo: GroupMessageInfo): Promise<GroupMessageInfo, Exception>

    fun deleteMessages(groupId: GroupId, messageIds: Collection<String>): Promise<Unit, Exception>

    fun deleteAllMessages(groupId: GroupId): Promise<Unit, Exception>

    fun isUserMemberOf(groupId: GroupId, userId: UserId): Promise<Boolean, Exception>

    fun markMessageAsDelivered(groupId: GroupId, messageId: String): Promise<GroupMessageInfo?, Exception>

    /* UIGroupService interface */
    fun getGroups(): Promise<List<GroupInfo>, Exception>

    fun getGroupConversations(): Promise<List<GroupConversation>, Exception>

    fun markConversationAsRead(groupId: GroupId): Promise<Unit, Exception>

    fun getMembersWithInfo(groupId: GroupId): Promise<List<ContactInfo>, Exception>

    fun join(groupInfo: GroupInfo, members: Set<UserId>): Promise<Unit, Exception>

    fun part(groupId: GroupId): Promise<Boolean, Exception>

    fun block(groupId: GroupId): Promise<Unit, Exception>

    fun unblock(groupId: GroupId): Promise<Unit, Exception>

    fun getBlockList(): Promise<Set<GroupId>, Exception>

    fun getLastMessages(groupId: GroupId, startingAt: Int, count: Int): Promise<List<GroupMessageInfo>, Exception>
}
