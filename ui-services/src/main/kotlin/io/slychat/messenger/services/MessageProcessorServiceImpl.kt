package io.slychat.messenger.services

import io.slychat.messenger.core.UserId
import io.slychat.messenger.core.currentTimestamp
import io.slychat.messenger.core.persistence.*
import nl.komponents.kovenant.Promise
import nl.komponents.kovenant.functional.bind
import org.slf4j.LoggerFactory
import rx.Observable
import rx.subjects.PublishSubject
import java.util.*

class MessageProcessorServiceImpl(
    private val contactsService: ContactsService,
    private val messagePersistenceManager: MessagePersistenceManager,
    private val groupPersistenceManager: GroupPersistenceManager
) : MessageProcessorService {
    private val log = LoggerFactory.getLogger(javaClass)

    private val newMessagesSubject = PublishSubject.create<MessageBundle>()
    override val newMessages: Observable<MessageBundle> = newMessagesSubject

    //XXX I think I'm just gonna do the less efficient route of processing each message at once... it'll simplify things
    //since we need to process things like group events, sync self sent (this needs to be processed in the proper order as well), as well as normal text messages
    //this is an issue for text messages though, since bundles work great for not setting off like 30 notifications in a row...
    //maybe just use some rx operator to get around this? nfi
    //or maybe just listen for events and buffer them until this finalizes?
    //we can't move on until we've processed all these messages
    override fun processMessage(userId: UserId, wrapper: SlyMessageWrapper): Promise<Unit, Exception> {
        val m = wrapper.message
        val messageId = wrapper.messageId

        return when (m) {
            is TextMessageWrapper -> handleTextMessage(userId, messageId, m.m)

            is GroupEventWrapper -> handleGroupMessage(userId, messageId, m.m)

            else -> {
                log.error("Unhandled message type: {}", m.javaClass.name)
                throw IllegalArgumentException("Unhandled message type: ${m.javaClass.name}")
            }
        }
    }

    private fun handleTextMessage(userId: UserId, messageId: String, m: TextMessage): Promise<Unit, Exception> {
        val messageInfo = MessageInfo.newReceived(messageId, m.message, m.timestamp, currentTimestamp(), 0)

        return messagePersistenceManager.addMessage(userId, messageInfo) mapUi { messageInfo ->
            val bundle = MessageBundle(userId, listOf(messageInfo))
            newMessagesSubject.onNext(bundle)
        } fail { e ->
            log.error("Unable to store decrypted messages: {}", e.message, e)
        }
    }

    private fun handleGroupMessage(userId: UserId, messageId: String, m: GroupEvent): Promise<Unit, Exception> {
        return groupPersistenceManager.getGroupInfo(m.id) bind { groupInfo ->
            when (m) {
                is GroupInvitation -> handleGroupInvitation(groupInfo, m)
                is GroupJoin -> runIfJoined(groupInfo) { checkGroupMembership(userId, m.id) { handleGroupJoin(m) } }
                else -> throw IllegalArgumentException("Invalid GroupEvent: ${m.javaClass.name}")
            }
        }
    }

    private inline fun runIfJoined(groupInfo: GroupInfo?, action: () -> Promise<Unit, Exception>) : Promise<Unit, Exception> {
        return if (groupInfo == null || groupInfo.membershipLevel != GroupMembershipLevel.JOINED)
            return Promise.ofSuccess(Unit)
        else
            action()
    }

    private fun handleGroupJoin(m: GroupJoin): Promise<Unit, Exception> {
        return groupPersistenceManager.addMember(m.id, m.joined) mapUi { wasAdded ->
            if (wasAdded)
                log.info("User {} joined group {}", m.joined, m.id)

            Unit
        }
    }

    /** Only runs the given action if the user is a member of the given group. Otherwise, logs a warning. */
    private fun checkGroupMembership(userId: UserId, id: GroupId, action: () -> Promise<Unit, Exception>): Promise<Unit, Exception> {
        return groupPersistenceManager.isUserMemberOf(userId, id) bind { isMember ->
            if (isMember)
                action()
            else {
                log.warn("Received a group message for group <{}> from non-member <{}>, ignoring", id, userId.long)
                Promise.ofSuccess(Unit)
            }
        }
    }

    private fun handleGroupInvitation(groupInfo: GroupInfo?, m: GroupInvitation): Promise<Unit, Exception> {
        val members = HashSet(m.members)

        //XXX should we bother checking if the sender is a member of the group as well? seems pointless

        return if (groupInfo == null || groupInfo.membershipLevel == GroupMembershipLevel.PARTED) {
                contactsService.addMissingContacts(m.members) bind { invalidIds ->
                    members.removeAll(invalidIds)
                    val info = GroupInfo(m.id, m.name, true, GroupMembershipLevel.JOINED)
                    groupPersistenceManager.joinGroup(info, members)
                }
            }
            else
                Promise.ofSuccess(Unit)
    }
}