package io.slychat.messenger.services.messaging

import io.slychat.messenger.core.UserId
import io.slychat.messenger.core.currentTimestamp
import io.slychat.messenger.core.persistence.*
import io.slychat.messenger.core.persistence.sqlite.InvalidMessageLevelException
import io.slychat.messenger.services.*
import io.slychat.messenger.services.contacts.ContactsService
import io.slychat.messenger.services.crypto.MessageCipherService
import nl.komponents.kovenant.Promise
import nl.komponents.kovenant.functional.map
import org.slf4j.LoggerFactory
import rx.Observable
import rx.Subscription
import java.util.*

class MessageProcessorImpl(
    private val selfId: UserId,
    private val contactsService: ContactsService,
    private val messageService: MessageService,
    private val messageCipherService: MessageCipherService,
    private val groupService: GroupService,
    uiEvents: Observable<UIEvent>
) : MessageProcessor {
    private val log = LoggerFactory.getLogger(javaClass)

    private var currentlySelectedChatUser: UserId? = null
    private var currentlySelectedGroup: GroupId? = null

    private var subscription: Subscription? = null

    init {
        subscription = uiEvents.subscribe { onUiEvent(it) }
    }

    override fun init() {}

    override fun shutdown() {
        subscription?.unsubscribe()
        subscription = null
    }

    private fun onUiEvent(event: UIEvent) {
        when (event) {
            is UIEvent.PageChange -> {
                currentlySelectedChatUser = null
                currentlySelectedGroup = null

                when (event.page) {
                    PageType.CONVO -> {
                        val userId = UserId(event.extra.toLong())
                        currentlySelectedChatUser = userId
                    }

                    PageType.GROUP -> {
                        val groupId = GroupId(event.extra)
                        currentlySelectedGroup = groupId
                    }
                }
            }
        }
    }

    override fun processMessage(sender: UserId, message: SlyMessage): Promise<Unit, Exception> {

        return when (message) {
            is SlyMessage.Text -> handleTextMessage(sender, message.m)

            is SlyMessage.GroupEvent -> handleGroupMessage(sender, message.m)

            is SlyMessage.Sync -> handleSyncMessage(sender, message.m)

            is SlyMessage.Control -> handleControlMessage(sender, message.m)
        }
    }

    private fun  handleControlMessage(sender: UserId, m: ControlMessage): Promise<Unit, Exception> {
        return when (m) {
            is ControlMessage.WasAdded -> {
                log.info("{} added us as a contact", sender)
                contactsService.addById(sender) map { Unit }
            }
        }
    }

    private fun handleSyncMessage(sender: UserId, m: SyncMessage): Promise<Unit, Exception> {
        return if (sender != selfId)
            Promise.ofFail(SyncMessageFromOtherSecurityException(sender, m.javaClass.simpleName))
        else {
            when (m) {
                is SyncMessage.NewDevice -> {
                    log.info("Received new device message")
                    messageCipherService.addSelfDevice(m.deviceInfo)
                }

                is SyncMessage.SelfMessage -> {
                    log.info("Received self sent message")
                    handleSelfMessage(m)
                }

                is SyncMessage.AddressBookSync -> {
                    log.info("Received self sync message")
                    Promise.ofSuccess(contactsService.doAddressBookPull())
                }

                is SyncMessage.MessageExpired -> {
                    log.info("Received message expired for {}/{}", m.conversationId, m.messageId)
                    messageService.expireMessages(mapOf(m.conversationId to listOf(m.messageId.string)), true)
                }
            }
        }
    }

    private fun handleSelfMessage(m: SyncMessage.SelfMessage): Promise<Unit, Exception> {
        val sentMessageInfo = m.sentMessageInfo

        val messageInfo = sentMessageInfo.toMessageInfo()
        val conversationMessageInfo = ConversationMessageInfo(
            null,
            messageInfo
        )

        val recipient = sentMessageInfo.recipient

        return when (recipient) {
            //if we add a new contact, then message them right away, the SelfMessage'll get here before the AddressBookSync one
            is Recipient.User -> contactsService.addMissingContacts(setOf(recipient.id)) bindUi {
                addSingleMessage(recipient.id, conversationMessageInfo)
            }
            is Recipient.Group -> addGroupMessage(recipient.id, conversationMessageInfo)
        }
    }

    private fun addSingleMessage(userId: UserId, conversationMessageInfo: ConversationMessageInfo): Promise<Unit, Exception> {
        val conversationId = userId.toConversationId()
        return messageService.addMessage(conversationId, conversationMessageInfo) bindRecoverForUi { e: InvalidMessageLevelException ->
            log.debug("User doesn't have appropriate message level, upgrading")

            contactsService.allowAll(userId) bindUi {
                messageService.addMessage(conversationId, conversationMessageInfo)
            }
        }
    }

    private fun handleTextMessage(sender: UserId, m: TextMessage): Promise<Unit, Exception> {
        val groupId = m.groupId

        val isRead = if (groupId == null)
            sender == currentlySelectedChatUser
        else
            groupId == currentlySelectedGroup

        val messageInfo = MessageInfo.newReceived(m.id.string, m.message, m.timestamp, currentTimestamp(), isRead, m.ttl)
        val conversationInfo = ConversationMessageInfo(sender, messageInfo)

        return if (groupId == null) {
            addSingleMessage(sender, conversationInfo)
        }
        else {
            groupService.getInfo(groupId) bindUi { groupInfo ->
                runIfJoinedAndUserIsMember(groupInfo, sender) { addGroupMessage(groupId, conversationInfo) }
            }
        }
    }

    private fun addGroupMessage(groupId: GroupId, conversationMessageInfo: ConversationMessageInfo): Promise<Unit, Exception> {
        return messageService.addMessage(groupId.toConversationId(), conversationMessageInfo)
    }

    private fun handleGroupMessage(sender: UserId, m: GroupEventMessage): Promise<Unit, Exception> {
        return groupService.getInfo(m.id) bindUi { groupInfo ->
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
        return groupService.isUserMemberOf(id, sender) bindUi { isMember ->
            if (isMember)
                action()
            else {
                log.warn("Received a group message for group <{}> from non-member <{}>, ignoring", id.string, sender.long)
                Promise.ofSuccess(Unit)
            }
        }
    }

    private fun handleGroupJoin(m: GroupEventMessage.Join): Promise<Unit, Exception> {
        return contactsService.addMissingContacts(m.joined) bindUi { invalidIds ->
            val remaining = HashSet(m.joined)
            remaining.removeAll(invalidIds)

            if (remaining.isNotEmpty()) {
                groupService.addMembers(m.id, remaining)
            }
            else {
                log.warn("Received a join for group {} but joining user id {} is invalid")
                Promise.ofSuccess(Unit)
            }
        }
    }

    private fun handleGroupPart(groupId: GroupId, sender: UserId): Promise<Unit, Exception> {
        return groupService.removeMember(groupId, sender)
    }

    private fun handleGroupInvitation(sender: UserId, groupInfo: GroupInfo?, m: GroupEventMessage.Invitation): Promise<Unit, Exception> {
        val members = HashSet(m.members)

        //since we send ourselves invitations as well, we don't include ourselves in the list
        if (sender != selfId)
            members.add(sender)

        return if (groupInfo == null || groupInfo.membershipLevel == GroupMembershipLevel.PARTED) {
            //we already have the sender added, so we don't need to include them
            contactsService.addMissingContacts(m.members) bindUi { invalidIds ->
                members.removeAll(invalidIds)
                val info = GroupInfo(m.id, m.name, GroupMembershipLevel.JOINED)
                groupService.join(info, members)
            }
        }
        else
            Promise.ofSuccess(Unit)
    }
}