package io.slychat.messenger.services.ui.impl

import io.slychat.messenger.core.mapToSet
import io.slychat.messenger.core.persistence.GroupConversation
import io.slychat.messenger.core.persistence.GroupId
import io.slychat.messenger.core.persistence.toConversationId
import io.slychat.messenger.services.GroupService
import io.slychat.messenger.services.di.UserComponent
import io.slychat.messenger.services.mapUi
import io.slychat.messenger.services.messaging.GroupEvent
import io.slychat.messenger.services.messaging.MessageService
import io.slychat.messenger.services.messaging.MessengerService
import io.slychat.messenger.services.ui.*
import nl.komponents.kovenant.Promise
import nl.komponents.kovenant.functional.map
import rx.Observable
import rx.Subscription
import java.util.*

class UIGroupServiceImpl(
    userSessionAvailable: Observable<UserComponent?>
) : UIGroupService {
    private val groupEventListeners = ArrayList<(UIGroupEvent) -> Unit>()

    private var groupEventsSub: Subscription? = null

    private var groupService: GroupService? = null
    private var messageService: MessageService? = null
    private var messengerService: MessengerService? = null

    init {
        userSessionAvailable.subscribe {
            if (it == null) {
                groupEventsSub?.unsubscribe()
                groupEventsSub = null

                groupService = null
                messengerService = null
                messageService = null
            }
            else {
                groupService = it.groupService
                messengerService = it.messengerService
                messageService = it.messageService

                groupEventsSub = it.groupService.groupEvents.subscribe { onGroupEvent(it) }
            }
        }
    }

    private fun onGroupEvent(ev: GroupEvent) {
        val uiEv = when (ev) {
            is GroupEvent.NewGroup -> UIGroupEvent.NewGroup(ev.id, ev.members)
            is GroupEvent.Joined -> UIGroupEvent.Joined(ev.id, ev.newMembers)
            is GroupEvent.Parted -> UIGroupEvent.Parted(ev.id, ev.member)
        }

        groupEventListeners.forEach { it(uiEv) }
    }

    private fun getMessengerServiceOrThrow(): MessengerService {
        return messengerService ?: throw IllegalStateException("No user session")
    }

    private fun getGroupServiceOrThrow(): GroupService {
        return groupService ?: throw IllegalStateException("No user session")
    }

    private fun getMessageServiceOrThrow(): MessageService {
        return messageService ?: throw IllegalStateException("No user session")
    }

    override fun addGroupEventListener(listener: (UIGroupEvent) -> Unit) {
        groupEventListeners.add(listener)
    }

    override fun getGroups(): Promise<List<UIGroupInfo>, Exception> {
        return getGroupServiceOrThrow().getGroups() map {
            it.map { UIGroupInfo(it.id, it.name) }
        }
    }

    private fun GroupConversation.toUi(): UIGroupConversation {
        val groupInfo = UIGroupInfo(group.id, group.name)
        val convoInfo = UIGroupConversationInfo(info.lastSpeaker,info.unreadMessageCount, info.lastMessage, info.lastTimestamp)

        return UIGroupConversation(groupInfo, convoInfo)
    }

    override fun getGroupConversations(): Promise<List<UIGroupConversation>, Exception> {
        return getGroupServiceOrThrow().getGroupConversations() map {
            it.map { it.toUi() }
        }
    }

    override fun inviteUsers(groupId: GroupId, contacts: List<UIContactInfo>): Promise<Unit, Exception> {
        val ids = contacts.mapToSet { it.id }
        return getMessengerServiceOrThrow().inviteUsersToGroup(groupId, ids)
    }

    override fun createNewGroup(name: String, initialMembers: List<UIContactInfo>): Promise<GroupId, Exception> {
        val ids = initialMembers.mapToSet { it.id }

        return getMessengerServiceOrThrow().createNewGroup(name, ids)
    }

    override fun getMembers(groupId: GroupId): Promise<List<UIContactInfo>, Exception> {
        return getGroupServiceOrThrow().getMembersWithInfo(groupId) map { it.toUI() }
    }

    override fun part(groupId: GroupId): Promise<Boolean, Exception> {
        return getMessengerServiceOrThrow().partGroup(groupId)
    }

    override fun block(groupId: GroupId): Promise<Unit, Exception> {
        return getMessengerServiceOrThrow().blockGroup(groupId)
    }

    override fun unblock(groupId: GroupId): Promise<Unit, Exception> {
        return getGroupServiceOrThrow().unblock(groupId)
    }

    override fun getBlockList(): Promise<Set<GroupId>, Exception> {
        return getGroupServiceOrThrow().getBlockList()
    }

    override fun getLastMessages(groupId: GroupId, startingAt: Int, count: Int): Promise<List<UIGroupMessage>, Exception> {
        return getMessageServiceOrThrow().getLastMessages(groupId.toConversationId(), startingAt, count) map {
            it.map { UIGroupMessage(it.speaker, it.info.toUI()) }
        }
    }

    override fun deleteAllMessages(groupId: GroupId): Promise<Unit, Exception> {
        return getMessageServiceOrThrow().deleteAllMessages(groupId.toConversationId())
    }

    override fun deleteMessagesFor(groupId: GroupId, messageIds: List<String>): Promise<Unit, Exception> {
        return getMessageServiceOrThrow().deleteMessages(groupId.toConversationId(), messageIds, false)
    }

    override fun getInfo(groupId: GroupId): Promise<UIGroupInfo?, Exception> {
        return getGroupServiceOrThrow().getInfo(groupId) mapUi { maybeGroupInfo ->
            maybeGroupInfo?.let { UIGroupInfo(it.id, it.name) }
        }
    }

    override fun startMessageExpiration(groupId: GroupId, messageId: String): Promise<Unit, Exception> {
        return getMessageServiceOrThrow().startMessageExpiration(groupId.toConversationId(), messageId)
    }

    override fun clearListeners() {
        groupEventListeners.clear()
    }
}