package io.slychat.messenger.services

import io.slychat.messenger.core.UserId
import io.slychat.messenger.core.persistence.*
import io.slychat.messenger.services.messaging.GroupEvent
import nl.komponents.kovenant.Promise
import rx.Observable

interface GroupService {
    val groupEvents: Observable<GroupEvent>

    /* UIGroupService interface */
    fun getGroups(): Promise<List<GroupInfo>, Exception>

    fun getGroupConversations(): Promise<List<GroupConversation>, Exception>

    fun inviteUsers(groupId: GroupId, contact: Set<UserId>): Promise<Unit, Exception>

    fun markConversationAsRead(groupId: GroupId): Promise<Unit, Exception>

    fun createNewGroup(name: String, initialMembers: Set<UserId>): Promise<Unit, Exception>

    fun getMembers(groupId: GroupId): Promise<List<ContactInfo>, Exception>

    fun part(groupId: GroupId): Promise<Boolean, Exception>

    fun block(groupId: GroupId): Promise<Unit, Exception>

    fun unblock(groupId: GroupId): Promise<Unit, Exception>

    fun getBlockList(): Promise<Set<GroupId>, Exception>

    fun getLastMessages(groupId: GroupId, startingAt: Int, count: Int): Promise<List<GroupMessageInfo>, Exception>

    fun deleteAllMessages(groupId: GroupId): Promise<Unit, Exception>

    fun deleteMessages(groupId: GroupId, messageIds: Collection<String>): Promise<Unit, Exception>
}
