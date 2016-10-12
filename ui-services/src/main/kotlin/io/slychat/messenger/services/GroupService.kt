package io.slychat.messenger.services

import io.slychat.messenger.core.UserId
import io.slychat.messenger.core.persistence.ContactInfo
import io.slychat.messenger.core.persistence.GroupConversation
import io.slychat.messenger.core.persistence.GroupId
import io.slychat.messenger.core.persistence.GroupInfo
import io.slychat.messenger.services.messaging.GroupEvent
import nl.komponents.kovenant.Promise
import rx.Observable

interface GroupService {
    val groupEvents: Observable<GroupEvent>

    fun addMembers(groupId: GroupId, users: Set<UserId>): Promise<Unit, Exception>

    fun removeMember(groupId: GroupId, userId: UserId): Promise<Unit, Exception>

    fun getMembers(groupId: GroupId): Promise<Set<UserId>, Exception>

    fun getNonBlockedMembers(groupId: GroupId): Promise<Set<UserId>, Exception>

    fun getInfo(groupId: GroupId): Promise<GroupInfo?, Exception>

    fun isUserMemberOf(groupId: GroupId, userId: UserId): Promise<Boolean, Exception>

    /* UIGroupService interface */
    fun getGroups(): Promise<List<GroupInfo>, Exception>

    fun getGroupConversations(): Promise<List<GroupConversation>, Exception>

    fun getGroupConversation(groupId: GroupId): Promise<GroupConversation?, Exception>

    fun getMembersWithInfo(groupId: GroupId): Promise<List<ContactInfo>, Exception>

    fun join(groupInfo: GroupInfo, members: Set<UserId>): Promise<Unit, Exception>

    fun part(groupId: GroupId): Promise<Boolean, Exception>

    fun block(groupId: GroupId): Promise<Unit, Exception>

    fun unblock(groupId: GroupId): Promise<Unit, Exception>

    fun getBlockList(): Promise<Set<GroupId>, Exception>
}
