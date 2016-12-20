package io.slychat.messenger.android.activites.services.impl

import android.support.v7.app.AppCompatActivity
import io.slychat.messenger.android.AndroidApp
import io.slychat.messenger.android.activites.services.GroupService
import io.slychat.messenger.core.UserId
import io.slychat.messenger.core.persistence.ContactInfo
import io.slychat.messenger.core.persistence.GroupConversation
import io.slychat.messenger.core.persistence.GroupId
import io.slychat.messenger.core.persistence.GroupInfo
import io.slychat.messenger.services.messaging.GroupEvent
import nl.komponents.kovenant.Promise
import nl.komponents.kovenant.functional.bind
import nl.komponents.kovenant.functional.map
import nl.komponents.kovenant.ui.successUi
import rx.Subscription

class GroupServiceImpl(activity: AppCompatActivity): GroupService {
    private val app = AndroidApp.get(activity)
    private val groupService = app.getUserComponent().groupService
    private val contactService = app.getUserComponent().contactsService
    private val messengerService = app.getUserComponent().messengerService

    private var groupConvo: MutableMap<GroupId, GroupConversation> = mutableMapOf()

    private lateinit var uiListener: (GroupEvent) -> Unit
    private var groupListener: Subscription? = null

    override fun createGroup(name: String, userIds: Set<UserId>): Promise<GroupId, Exception> {
        return messengerService.createNewGroup(name, userIds)
    }

    override fun fetchGroupConversations(): Promise<MutableMap<GroupId, GroupConversation>, Exception> {
        groupConvo = mutableMapOf()
        return groupService.getGroupConversations() map { conversations ->
            conversations.forEach { conversation ->
                groupConvo.put(conversation.group.id, conversation)
            }
            groupConvo
        }
    }

    override fun addGroupListener(listener: (GroupEvent) -> Unit) {
        uiListener = listener
        groupListener?.unsubscribe()
        groupListener = groupService.groupEvents.subscribe { event ->
            groupEventUpdateUI(event)
        }
    }

    override fun removeListener() {
        groupListener?.unsubscribe()
    }

    override fun getMembers(groupId: GroupId): Promise<Set<UserId>, Exception> {
        return groupService.getMembers(groupId)
    }

    override fun getGroupInfo(groupId: GroupId): Promise<GroupInfo?, Exception> {
        return groupService.getInfo(groupId)
    }

    override fun blockGroup(groupId: GroupId): Promise<Unit, Exception> {
        return groupService.block(groupId)
    }
    
    override fun deleteGroup(groupId: GroupId): Promise<Boolean, Exception> {
        return groupService.part(groupId)
    }

    override fun getMembersInfo(groupId: GroupId): Promise<Map<UserId, ContactInfo>, Exception> {
        val contactMap = mutableMapOf<UserId, ContactInfo>()
        val membersInfo = mutableMapOf<UserId, ContactInfo>()

        return contactService.getAll() map { contacts ->
            contacts.forEach {
                contactMap.put(it.id, it)
            }
        } bind {
            groupService.getMembers(groupId) map { members ->
                members.forEach {
                    val contactInfo = contactMap[it]
                    if (contactInfo !== null)
                        membersInfo.put(it, contactInfo)
                }
                membersInfo
            }
        }
    }

    private fun groupEventUpdateUI(event: GroupEvent) {
        uiListener.invoke(event)
    }
}