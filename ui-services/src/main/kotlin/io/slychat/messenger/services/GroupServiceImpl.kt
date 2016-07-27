package io.slychat.messenger.services

import io.slychat.messenger.core.UserId
import io.slychat.messenger.core.persistence.*
import io.slychat.messenger.services.messaging.GroupEvent
import io.slychat.messenger.services.messaging.MessageProcessor
import nl.komponents.kovenant.Promise
import nl.komponents.kovenant.functional.bind
import rx.Observable

//TODO should move the group message generation to here; in a hurry now so do it later
class GroupServiceImpl(
    private val groupPersistenceManager: GroupPersistenceManager,
    private val contactsPersistenceManager: ContactsPersistenceManager,
    private val messageProcessor: MessageProcessor
) : GroupService {
    override val groupEvents: Observable<GroupEvent>
        get() = messageProcessor.groupEvents

    override fun getGroups(): Promise<List<GroupInfo>, Exception> {
        return groupPersistenceManager.getList()
    }

    override fun getGroupConversations(): Promise<List<GroupConversation>, Exception> {
        return groupPersistenceManager.getAllConversations()
    }

    override fun inviteUsers(groupId: GroupId, contact: Set<UserId>): Promise<Unit, Exception> {
        TODO()
    }

    override fun markConversationAsRead(groupId: GroupId): Promise<Unit, Exception> {
        return groupPersistenceManager.markConversationAsRead(groupId)
    }

    override fun createNewGroup(name: String, initialMembers: Set<UserId>): Promise<Unit, Exception> {
        TODO()
    }

    override fun getMembers(groupId: GroupId): Promise<List<ContactInfo>, Exception> {
        return groupPersistenceManager.getMembers(groupId) bind {
            contactsPersistenceManager.get(it)
        }
    }

    override fun part(groupId: GroupId): Promise<Boolean, Exception> {
        return groupPersistenceManager.part(groupId)
    }

    override fun block(groupId: GroupId): Promise<Unit, Exception> {
        return groupPersistenceManager.block(groupId)
    }

    override fun unblock(groupId: GroupId): Promise<Unit, Exception> {
        return groupPersistenceManager.unblock(groupId)
    }

    override fun getBlockList(): Promise<Set<GroupId>, Exception> {
        return groupPersistenceManager.getBlockList()
    }

    override fun getLastMessages(groupId: GroupId, startingAt: Int, count: Int): Promise<List<GroupMessageInfo>, Exception> {
        return groupPersistenceManager.getLastMessages(groupId, startingAt, count)
    }

    override fun deleteAllMessages(groupId: GroupId): Promise<Unit, Exception> {
        return groupPersistenceManager.deleteAllMessages(groupId)
    }

    override fun deleteMessages(groupId: GroupId, messageIds: Collection<String>): Promise<Unit, Exception> {
        return groupPersistenceManager.deleteMessages(groupId, messageIds)
    }
}