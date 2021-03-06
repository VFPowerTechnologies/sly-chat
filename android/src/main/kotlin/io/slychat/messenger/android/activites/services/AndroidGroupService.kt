package io.slychat.messenger.android.activites.services

import io.slychat.messenger.core.UserId
import io.slychat.messenger.core.persistence.ContactInfo
import io.slychat.messenger.core.persistence.GroupConversation
import io.slychat.messenger.core.persistence.GroupId
import io.slychat.messenger.core.persistence.GroupInfo
import io.slychat.messenger.services.messaging.GroupEvent
import nl.komponents.kovenant.Promise

interface AndroidGroupService {

    fun addGroupListener(listener: (GroupEvent) -> Unit)

    fun clearListeners()

    fun createGroup(name: String, userIds: Set<UserId>): Promise<GroupId, Exception>

    fun fetchGroupConversations(): Promise<MutableMap<GroupId, GroupConversation>, Exception>

    fun getBlockedGroups(): Promise<List<GroupInfo>, Exception>

    fun getMembers(groupId: GroupId): Promise<Set<UserId>, Exception>

    fun getGroupInfo(groupId: GroupId): Promise<GroupInfo?, Exception>

    fun blockGroup(groupId: GroupId): Promise<Unit, Exception>

    fun unblockGroup(groupId: GroupId): Promise<Unit, Exception>

    fun deleteGroup(groupId: GroupId): Promise<Boolean, Exception>

    fun getMembersInfo(groupId: GroupId): Promise<Map<UserId, ContactInfo>, Exception>
}