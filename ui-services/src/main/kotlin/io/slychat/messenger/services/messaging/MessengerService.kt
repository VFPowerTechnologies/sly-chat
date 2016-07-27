package io.slychat.messenger.services.messaging

import io.slychat.messenger.core.UserId
import io.slychat.messenger.core.persistence.*
import nl.komponents.kovenant.Promise
import rx.Observable

interface MessengerService {
    fun init()
    fun shutdown()

    fun addOfflineMessages(offlineMessages: List<Package>): Promise<Unit, Exception>

    val newMessages: Observable<MessageBundle>
    val messageUpdates: Observable<MessageBundle>

    /* UIMessengerService interface */
    fun sendMessageTo(userId: UserId, message: String): Promise<MessageInfo, Exception>
    fun sendGroupMessageTo(groupId: GroupId, message: String): Promise<GroupMessageInfo, Exception>
    fun createNewGroup(groupName: String, initialMembers: Set<UserId>): Promise<Unit, Exception>
    fun inviteUsersToGroup(groupId: GroupId, newMembers: Set<UserId>): Promise<Unit, Exception>
    fun partGroup(groupId: GroupId): Promise<Boolean, Exception>
    fun blockGroup(groupId: GroupId): Promise<Unit, Exception>
    fun getLastMessagesFor(userId: UserId, startingAt: Int, count: Int): Promise<List<MessageInfo>, Exception>
    fun getConversations(): Promise<List<Conversation>, Exception>
    fun markConversationAsRead(userId: UserId): Promise<Unit, Exception>
    fun deleteMessages(userId: UserId, messageIds: List<String>): Promise<Unit, Exception>
    fun deleteAllMessages(userId: UserId): Promise<Unit, Exception>
    fun deleteGroupMessages(groupId: GroupId, messageIds: List<String>): Promise<Unit, Exception>
    fun deleteAllGroupMessages(groupId: GroupId): Promise<Unit, Exception>
}