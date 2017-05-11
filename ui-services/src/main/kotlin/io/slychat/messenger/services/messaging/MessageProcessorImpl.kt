package io.slychat.messenger.services.messaging

import io.slychat.messenger.core.UserId
import io.slychat.messenger.core.crypto.generateFileId
import io.slychat.messenger.core.files.SharedFrom
import io.slychat.messenger.core.files.UserMetadata
import io.slychat.messenger.core.persistence.*
import io.slychat.messenger.core.persistence.sqlite.InvalidMessageLevelException
import io.slychat.messenger.services.*
import io.slychat.messenger.services.contacts.ContactsService
import io.slychat.messenger.services.crypto.MessageCipherService
import io.slychat.messenger.services.files.StorageService
import io.slychat.messenger.services.files.cache.AttachmentService
import nl.komponents.kovenant.Promise
import nl.komponents.kovenant.functional.map
import org.slf4j.LoggerFactory
import rx.Observable
import rx.subscriptions.CompositeSubscription
import java.util.*

class MessageProcessorImpl(
    private val selfId: UserId,
    private val contactsService: ContactsService,
    private val messageService: MessageService,
    private val storageService: StorageService,
    private val messageCipherService: MessageCipherService,
    private val groupService: GroupService,
    private val relayClock: RelayClock,
    private val attachmentService: AttachmentService,
    uiVisibility: Observable<Boolean>,
    uiEvents: Observable<UIEvent>
) : MessageProcessor {
    private val log = LoggerFactory.getLogger(javaClass)

    private var isUiVisible = false
    private var currentlySelectedChatUser: UserId? = null
    private var currentlySelectedGroup: GroupId? = null

    private var subscriptions = CompositeSubscription()

    init {
        subscriptions.add(uiEvents.subscribe { onUiEvent(it) })
        subscriptions.add(uiVisibility.subscribe { onUiVisibilityChange(it) })
    }

    private fun onUiVisibilityChange(isVisible: Boolean) {
        isUiVisible = isVisible
    }

    override fun init() {}

    override fun shutdown() {
        subscriptions.clear()
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

                    else -> {}
                }
            }
        }
    }

    override fun processMessage(sender: UserId, message: SlyMessage): Promise<Unit, Exception> = when (message) {
        is SlyMessage.Text -> handleTextMessage(sender, message.m)

        is SlyMessage.GroupEvent -> handleGroupMessage(sender, message.m)

        is SlyMessage.Sync -> handleSyncMessage(sender, message.m)

        is SlyMessage.Control -> handleControlMessage(sender, message.m)
    }

    private fun handleControlMessage(sender: UserId, m: ControlMessage): Promise<Unit, Exception> {
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
                    log.info("Received address book sync message")
                    Promise.ofSuccess(contactsService.doAddressBookPull())
                }

                is SyncMessage.MessageExpired -> {
                    log.info("Received message expired for {}/{}", m.conversationId, m.messageId)
                    messageService.expireMessages(mapOf(m.conversationId to listOf(m.messageId.string)), true)
                }

                is SyncMessage.MessagesRead -> {
                    log.info("Messages read for {}: {}", m.conversationId, m.messageIds)
                    messageService.markConversationMessagesAsRead(m.conversationId, m.messageIds.map { it.string })
                }

                is SyncMessage.MessagesDeleted -> {
                    log.info("Messages deleted for {}: {}", m.conversationId, m.messageIds)
                    messageService.deleteMessages(m.conversationId, m.messageIds.map { it.string }, true)
                }

                is SyncMessage.MessagesDeletedAll -> {
                    log.info("Messages until {} deleted for {}", m.lastMessageTimestamp, m.conversationId)
                    messageService.deleteAllMessagesUntil(m.conversationId, m.lastMessageTimestamp)
                }

                is SyncMessage.FileListSync -> {
                    log.info("Received file list sync message")
                    Promise.ofSuccess(storageService.sync())
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

        val conversationId = sentMessageInfo.conversationId

        val attachmentFileIds = messageInfo.attachments
            .filter { it.isInline }
            .map { it.fileId }

        return when (conversationId) {
            //if we add a new contact, then message them right away, the SelfMessage'll get here before the AddressBookSync one
            is ConversationId.User -> contactsService.addMissingContacts(setOf(conversationId.id)) bindUi {
                addSingleMessage(conversationId.id, conversationMessageInfo, emptyList())
            }
            is ConversationId.Group -> addGroupMessage(conversationId.id, conversationMessageInfo, emptyList())
        } bindUi {
            attachmentService.requestCache(attachmentFileIds)
        }
    }

    private fun addSingleMessage(userId: UserId, conversationMessageInfo: ConversationMessageInfo, receivedAttachments: List<ReceivedAttachment>): Promise<Unit, Exception> {
        val conversationId = userId.toConversationId()
        val sender = conversationMessageInfo.speaker

        return messageService.addMessage(conversationId, conversationMessageInfo, receivedAttachments) bindRecoverForUi { e: InvalidMessageLevelException ->
            log.debug("User doesn't have appropriate message level, upgrading")

            contactsService.allowAll(userId) bindUi {
                messageService.addMessage(conversationId, conversationMessageInfo, receivedAttachments)
            }
        } mapUi {
            if (sender != null) {
                log.debug("Queuing received attachments: {}")
                attachmentService.addNewReceived(conversationId, sender, receivedAttachments)
            }
        }
    }

    private fun generateAttachmentInfo(conversationId: ConversationId, messageId: String, sender: UserId, groupId: GroupId?, textAttachments: List<TextMessageAttachment>): Pair<List<MessageAttachmentInfo>, List<ReceivedAttachment>> {
        val attachments = ArrayList<MessageAttachmentInfo>()
        val receivedAttachments = ArrayList<ReceivedAttachment>()

        val subdir = groupId?.toString() ?: sender.toString()

        textAttachments.forEachIndexed { i, a ->
            val fileId = generateFileId()

            attachments.add(MessageAttachmentInfo(i, a.fileName, fileId, false))

            val userMetadata = UserMetadata(
                a.fileKey,
                a.cipherId,
                "/_attachments/$subdir",
                fileId,
                SharedFrom(sender, null)
            )

            receivedAttachments.add(ReceivedAttachment(AttachmentId(conversationId, messageId, i), a.fileId.string, a.shareKey, userMetadata, ReceivedAttachmentState.PENDING, null))
        }

        return attachments to receivedAttachments
    }

    private fun handleTextMessage(sender: UserId, m: TextMessage): Promise<Unit, Exception> {
        val groupId = m.groupId

        val isRead = if (isUiVisible) {
            if (groupId == null)
                sender == currentlySelectedChatUser
            else
                groupId == currentlySelectedGroup
        }
        else
            false

        val conversationId = if (groupId != null)
            groupId.toConversationId()
        else
            sender.toConversationId()

        val (attachments, receivedAttachments) = generateAttachmentInfo(conversationId, m.id.string, sender, groupId, m.attachments)

        val messageInfo = MessageInfo.newReceived(m.id.string, m.message, m.timestamp, relayClock.currentTime(), isRead, m.ttlMs, attachments)
        val conversationInfo = ConversationMessageInfo(sender, messageInfo)

        return if (groupId == null) {
            addSingleMessage(sender, conversationInfo, receivedAttachments)
        }
        else {
            groupService.getInfo(groupId) bindUi { groupInfo ->
                runIfJoinedAndUserIsMember(groupInfo, sender) { addGroupMessage(groupId, conversationInfo, receivedAttachments) }
            }
        }
    }

    private fun addGroupMessage(groupId: GroupId, conversationMessageInfo: ConversationMessageInfo, receivedAttachments: List<ReceivedAttachment>): Promise<Unit, Exception> {
        val conversationId = groupId.toConversationId()

        val sender = conversationMessageInfo.speaker

        return messageService.addMessage(conversationId, conversationMessageInfo, receivedAttachments) mapUi {
            if (sender != null) {
                log.debug("Queuing received attachments: {}")
                attachmentService.addNewReceived(conversationId, sender, receivedAttachments)
            }
        }
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