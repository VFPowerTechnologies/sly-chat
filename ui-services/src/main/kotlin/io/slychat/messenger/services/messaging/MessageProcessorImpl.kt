package io.slychat.messenger.services.messaging

import io.slychat.messenger.core.UserId
import io.slychat.messenger.core.currentTimestamp
import io.slychat.messenger.core.persistence.*
import io.slychat.messenger.services.contacts.ContactsService
import io.slychat.messenger.services.mapUi
import nl.komponents.kovenant.Promise
import nl.komponents.kovenant.functional.bind
import nl.komponents.kovenant.ui.successUi
import org.slf4j.LoggerFactory
import rx.Observable
import rx.subjects.PublishSubject
import java.util.*

class MessageProcessorImpl(
    private val contactsService: ContactsService,
    private val messagePersistenceManager: MessagePersistenceManager,
    private val groupPersistenceManager: GroupPersistenceManager
) : MessageProcessor {
    private val log = LoggerFactory.getLogger(javaClass)

    private val newMessagesSubject = PublishSubject.create<MessageBundle>()
    override val newMessages: Observable<MessageBundle> = newMessagesSubject

    private val groupEventSubject = PublishSubject.create<GroupEvent>()
    override val groupEvents: Observable<GroupEvent> = groupEventSubject

    override fun processMessage(sender: UserId, wrapper: SlyMessageWrapper): Promise<Unit, Exception> {
        val m = wrapper.message
        val messageId = wrapper.messageId

        return when (m) {
            is TextMessageWrapper -> handleTextMessage(sender, messageId, m.m)

            is GroupEventMessageWrapper -> handleGroupMessage(sender, m.m)

            else -> {
                log.error("Unhandled message type: {}", m.javaClass.name)
                throw IllegalArgumentException("Unhandled message type: ${m.javaClass.name}")
            }
        }
    }

    private fun handleTextMessage(sender: UserId, messageId: String, m: TextMessage): Promise<Unit, Exception> {
        val messageInfo = MessageInfo.newReceived(messageId, m.message, m.timestamp, currentTimestamp(), 0)

        val groupId = m.groupId
        return if (groupId == null) {
            messagePersistenceManager.addMessage(sender, messageInfo) mapUi { messageInfo ->
                val bundle = MessageBundle(sender, listOf(messageInfo))
                newMessagesSubject.onNext(bundle)
            }
        }
        else {
            groupPersistenceManager.getInfo(groupId) bind { groupInfo ->
                runIfJoinedAndUserIsMember(groupInfo, sender) { addGroupMessage(groupId, sender, messageInfo) }
            }
        }
    }

    private fun addGroupMessage(groupId: GroupId, sender: UserId, messageInfo: MessageInfo): Promise<Unit, Exception> {
        val groupMessageInfo = GroupMessageInfo(sender, messageInfo)
        return groupPersistenceManager.addMessage(groupId, groupMessageInfo) mapUi {
            newMessagesSubject.onNext(MessageBundle(sender, listOf(messageInfo)))
        }
    }

    private fun handleGroupMessage(sender: UserId, m: GroupEventMessage): Promise<Unit, Exception> {
        return groupPersistenceManager.getInfo(m.id) bind { groupInfo ->
            when (m) {
                is GroupEventMessage.Invitation -> handleGroupInvitation(sender, groupInfo, m)
                is GroupEventMessage.Join -> runIfJoinedAndUserIsMember(groupInfo, sender) { handleGroupJoin(m)  }
                is GroupEventMessage.Part -> runIfJoinedAndUserIsMember(groupInfo, sender) { handleGroupPart(m.id, sender) }
                else -> throw IllegalArgumentException("Invalid GroupEvent: ${m.javaClass.name}")
            }
        }
    }

    /** Only runs the given action if we're joined to the group and the sender is a member of said group. */
    private fun runIfJoinedAndUserIsMember(groupInfo: GroupInfo?, sender: UserId, action: () -> Promise<Unit, Exception>): Promise<Unit, Exception> {
        return if (groupInfo == null || groupInfo.membershipLevel != GroupMembershipLevel.JOINED)
            return Promise.ofSuccess(Unit)
        else
            checkGroupMembership(sender, groupInfo.id, action)

    }

    /** Only runs the given action if the user is a member of the given group. Otherwise, logs a warning. */
    private fun checkGroupMembership(sender: UserId, id: GroupId, action: () -> Promise<Unit, Exception>): Promise<Unit, Exception> {
        return groupPersistenceManager.isUserMemberOf(id, sender) bind { isMember ->
            if (isMember)
                action()
            else {
                log.warn("Received a group message for group <{}> from non-member <{}>, ignoring", id.string, sender.long)
                Promise.ofSuccess(Unit)
            }
        }
    }

    private fun handleGroupJoin(m: GroupEventMessage.Join): Promise<Unit, Exception> {
        return contactsService.addMissingContacts(m.joined) bind { invalidIds ->
            val remaining = HashSet(m.joined)
            remaining.removeAll(invalidIds)

            if (remaining.isNotEmpty()) {
                groupPersistenceManager.addMembers(m.id, remaining) mapUi { wasAdded ->
                    if (wasAdded.isNotEmpty()) {
                        log.info("Users {} joined group {}", wasAdded, m.id.string)
                        groupEventSubject.onNext(GroupEvent.Joined(m.id, wasAdded))
                    }

                    Unit
                }
            }
            else {
                log.warn("Received a join for group {} but joining user id {} is invalid")
                Promise.ofSuccess(Unit)
            }
        }
    }

    private fun handleGroupPart(groupId: GroupId, sender: UserId): Promise<Unit, Exception> {
        return groupPersistenceManager.removeMember(groupId, sender) mapUi { wasRemoved ->
            if (wasRemoved) {
                log.info("User {} has left group {}", sender, groupId.string)
                groupEventSubject.onNext(GroupEvent.Parted(groupId, sender))
            }
        }
    }

    private fun handleGroupInvitation(sender: UserId, groupInfo: GroupInfo?, m: GroupEventMessage.Invitation): Promise<Unit, Exception> {
        val members = HashSet(m.members)
        members.add(sender)

        return if (groupInfo == null || groupInfo.membershipLevel == GroupMembershipLevel.PARTED) {
            //we already have the sender added, so we don't need to include them
            contactsService.addMissingContacts(m.members) bind { invalidIds ->
                members.removeAll(invalidIds)
                val info = GroupInfo(m.id, m.name, true, GroupMembershipLevel.JOINED)
                groupPersistenceManager.join(info, members) successUi {
                    groupEventSubject.onNext(GroupEvent.NewGroup(m.id, members))
                }
            }
        }
        else
            Promise.ofSuccess(Unit)
    }
}