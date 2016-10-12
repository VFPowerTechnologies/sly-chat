package io.slychat.messenger.services.messaging

import io.slychat.messenger.core.UserId
import io.slychat.messenger.core.http.api.authentication.DeviceInfo
import io.slychat.messenger.core.persistence.ConversationId
import io.slychat.messenger.core.persistence.ConversationMessageInfo
import io.slychat.messenger.core.persistence.GroupId
import io.slychat.messenger.core.persistence.Package
import nl.komponents.kovenant.Promise

interface MessengerService {
    fun init()
    fun shutdown()

    fun addOfflineMessages(offlineMessages: List<Package>): Promise<Unit, Exception>

    /** Broadcast a new device to other accounts. */
    fun broadcastNewDevice(deviceInfo: DeviceInfo): Promise<Unit, Exception>

    fun broadcastMessageExpired(conversationId: ConversationId, messageId: String): Promise<Unit, Exception>

    fun broadcastMessagesRead(conversationId: ConversationId, messageIds: List<String>): Promise<Unit, Exception>

    fun broadcastDeleted(conversationId: ConversationId, messageIds: List<String>): Promise<Unit, Exception>

    fun broadcastDeletedAll(conversationId: ConversationId, lastMessageTimestamp: Long): Promise<Unit, Exception>

    /** Notify another user that you've added them as a contact. */
    fun notifyContactAdd(userIds: Collection<UserId>): Promise<Unit, Exception>

    /* UIMessengerService interface */
    fun sendMessageTo(userId: UserId, message: String, ttlMs: Long): Promise<Unit, Exception>
    fun sendGroupMessageTo(groupId: GroupId, message: String, ttlMs: Long): Promise<Unit, Exception>
    fun createNewGroup(groupName: String, initialMembers: Set<UserId>): Promise<GroupId, Exception>
    fun inviteUsersToGroup(groupId: GroupId, newMembers: Set<UserId>): Promise<Unit, Exception>
    fun partGroup(groupId: GroupId): Promise<Boolean, Exception>
    fun blockGroup(groupId: GroupId): Promise<Unit, Exception>
    fun getLastMessagesFor(userId: UserId, startingAt: Int, count: Int): Promise<List<ConversationMessageInfo>, Exception>
    fun markConversationAsRead(userId: UserId): Promise<Unit, Exception>
}