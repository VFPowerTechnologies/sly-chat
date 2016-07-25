package io.slychat.messenger.services.ui.impl

import io.slychat.messenger.core.mapToSet
import io.slychat.messenger.core.persistence.GroupConversation
import io.slychat.messenger.core.persistence.GroupId
import io.slychat.messenger.services.GroupService
import io.slychat.messenger.services.SlyApplication
import io.slychat.messenger.services.messaging.GroupEvent
import io.slychat.messenger.services.messaging.MessengerService
import io.slychat.messenger.services.ui.*
import nl.komponents.kovenant.Promise
import nl.komponents.kovenant.functional.map
import rx.Subscription
import java.util.*

class UIGroupServiceImpl(
    val app: SlyApplication
) : UIGroupService {
    private val groupEventListeners = ArrayList<(UIGroupEvent) -> Unit>()

    private var groupEventsSub: Subscription? = null

    private var groupService: GroupService? = null

    init {
        app.userSessionAvailable.subscribe {
            if (it == null) {
                groupEventsSub?.unsubscribe()
                groupEventsSub = null

                groupService = null
            }
            else {
                groupService = it.groupService

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
        return app.userComponent?.messengerService ?: throw IllegalStateException("No user session")
    }

    private fun getGroupServiceOrThrow(): GroupService {
        return groupService ?: throw IllegalStateException("No user session")
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
        val convoInfo = UIGroupConversationInfo(info.lastSpeaker,info.unreadCount, info.lastMessage, info.lastTimestamp)

        return UIGroupConversation(groupInfo, convoInfo)
    }

    override fun getGroupConversations(): Promise<List<UIGroupConversation>, Exception> {
        return getGroupServiceOrThrow().getGroupConversations() map {
            it.map { it.toUi() }
        }
    }

    override fun markConversationAsRead(groupId: GroupId): Promise<Unit, Exception> {
        return getGroupServiceOrThrow().markConversationAsRead(groupId)
    }

    override fun inviteUsers(groupId: GroupId, contacts: List<UIContactDetails>): Promise<Unit, Exception> {
        val ids = contacts.mapToSet { it.id }
        return getMessengerServiceOrThrow().inviteUsersToGroup(groupId, ids)
    }

    override fun createNewGroup(name: String, initialMembers: List<UIContactDetails>): Promise<Unit, Exception> {
        val ids = initialMembers.mapToSet { it.id }

        return getMessengerServiceOrThrow().createNewGroup(name, ids)
    }

    override fun part(groupId: GroupId): Promise<Boolean, Exception> {
        return getMessengerServiceOrThrow().partGroup(groupId)
    }

    override fun block(groupId: GroupId): Promise<Unit, Exception> {
        return getGroupServiceOrThrow().block(groupId)
    }

    override fun unblock(groupId: GroupId): Promise<Unit, Exception> {
        return getGroupServiceOrThrow().unblock(groupId)
    }

    override fun getBlockList(): Promise<Set<GroupId>, Exception> {
        return getGroupServiceOrThrow().getBlockList()
    }
}