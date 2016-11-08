package io.slychat.messenger.services.messaging

import com.fasterxml.jackson.databind.ObjectMapper
import io.slychat.messenger.core.UserId
import io.slychat.messenger.core.crypto.randomMessageId
import io.slychat.messenger.core.crypto.randomUUID
import io.slychat.messenger.core.currentTimestamp
import io.slychat.messenger.core.http.api.authentication.DeviceInfo
import io.slychat.messenger.core.persistence.*
import io.slychat.messenger.core.relay.ReceivedMessage
import io.slychat.messenger.core.relay.RelayClientEvent
import io.slychat.messenger.services.*
import io.slychat.messenger.services.contacts.ContactsService
import nl.komponents.kovenant.Promise
import nl.komponents.kovenant.functional.bind
import nl.komponents.kovenant.functional.map
import nl.komponents.kovenant.ui.successUi
import org.slf4j.LoggerFactory
import rx.subscriptions.CompositeSubscription
import java.util.*

/**
 * Handles:
 *
 * Preprocessing received packages by filtering out blocked users for MessageReceiver.
 *
 * Creating and serializing the proper SlyMessage objects for MessageSender.
 * Marking sent messages as delivered.
 */
class MessengerServiceImpl(
    private val contactsService: ContactsService,
    private val messageService: MessageService,
    private val groupService: GroupService,
    private val relayClientManager: RelayClientManager,
    private val messageSender: MessageSender,
    private val messageReceiver: MessageReceiver,
    private val relayClock: RelayClock,
    private val selfId: UserId
) : MessengerService {
    private val log = LoggerFactory.getLogger(javaClass)

    private val objectMapper = ObjectMapper()

    private val subscriptions = CompositeSubscription()

    init {
        subscriptions.add(relayClientManager.events.subscribe { onRelayEvent(it) })

        subscriptions.add(messageSender.messageSent.subscribe { onMessageSendRecord(it) })
    }

    override fun init() {
    }

    override fun shutdown() {
        subscriptions.clear()
    }

    private fun onMessageSendRecord(record: MessageSendRecord) {
        val metadata = record.metadata

        log.debug("Processing sent message {} (category: {})", metadata.messageId, metadata.category)

        //FIXME
        record as MessageSendRecord.Ok

        when (metadata.category) {
            MessageCategory.TEXT_SINGLE -> processSuccessfulSend(metadata, record.serverReceivedTimestamp)
            MessageCategory.TEXT_GROUP -> processSuccessfulSend(metadata, record.serverReceivedTimestamp)
            MessageCategory.OTHER -> {}
        }
    }

    private fun processSuccessfulSend(metadata: MessageMetadata, serverReceivedTimestamp: Long) {
        val messageId = metadata.messageId

        val conversationId = metadata.getConversationId()

        log.debug("Processing sent message {} to {}", messageId, conversationId)

        markMessageAsDelivered(conversationId, metadata, messageId, serverReceivedTimestamp)
    }

    private fun markMessageAsDelivered(conversationId: ConversationId, metadata: MessageMetadata, messageId: String, serverReceivedTimestamp: Long) {
        messageService.markMessageAsDelivered(conversationId, messageId, serverReceivedTimestamp) bindUi { conversationMessageInfo ->
            broadcastSentMessage(metadata, conversationMessageInfo)
        } fail { e ->
            log.error("Unable to mark message for conversation <<{}>> as delivered: {}", conversationId, messageId, e.message, e)
        }
    }

    private fun onRelayEvent(event: RelayClientEvent) {
        when (event) {
            is ReceivedMessage ->
                handleReceivedMessage(event)
        }
    }

    private fun handleReceivedMessage(event: ReceivedMessage) {
        val timestamp = currentTimestamp()
        //XXX this is kinda hacky...
        //the issue is that since offline messages are deserialized via jackson, using a byte array would require the
        //relay or web server to store them as base64; need to come back and fix this stuff
        val pkg = Package(PackageId(event.from, randomUUID()), timestamp, event.content)
        val packages = listOf(pkg)

        processPackages(packages) successUi {
            relayClientManager.sendMessageReceivedAck(event.messageId)
        }
    }

    private fun usersFromPackages(packages: List<Package>): Set<UserId> = packages.mapTo(HashSet()) { it.userId }

    /** Filter out packages which belong to blocked users, etc. */
    private fun filterBlacklisted(packages: List<Package>): Promise<List<Package>, Exception> {
        val users = usersFromPackages(packages)

        return contactsService.filterBlocked(users) map { allowedUsers ->
            val rejected = HashSet(users)
            rejected.removeAll(allowedUsers)
            if (rejected.isNotEmpty())
                log.info("Reject messages from users: {}", rejected.map { it.long })

            packages.filter { it.id.address.id in allowedUsers }
        }
    }

    private fun fetchMissingContactInfo(packages: List<Package>): Promise<List<Package>, Exception> {
        val users = HashSet<UserId>()
        users.addAll(packages.map { it.id.address.id })

        //TODO should probably blacklist invalid ids for a while; prevent using clients to ddos servers by
        //sending them a bunch of invalid ids constantly
        return contactsService.addMissingContacts(users) map { invalidIds ->
            packages.filter { !invalidIds.contains(it.userId) }
        } fail { e ->
            log.error("Failed to add packages to queue: {}", e.message, e)
        }
    }

    /** Filter out blocked users, fetch any missing contact info. */
    private fun preprocessPackages(packages: List<Package>): Promise<List<Package>, Exception> {
        return filterBlacklisted(packages) bind { filtered ->
            fetchMissingContactInfo(filtered)
        }
    }

    /** Drops blacklisted IDs, fetches missing contact data and passes results to MessageReceiverService. */
    private fun processPackages(packages: List<Package>): Promise<Unit, Exception> {
        //since when we receive stuff we're always online, it makes sense that we should handle the contact info fetch as well as part of this process
        //by doing so, we keep the actual decryption free of network requirements
        //XXX although we need it to add new group members anyways... just keep this here for now
        return preprocessPackages(packages) bind { filtered ->
            messageReceiver.processPackages(filtered)
        }
    }

    /* UIMessengerService interface */

    override fun sendMessageTo(userId: UserId, message: String, ttlMs: Long): Promise<Unit, Exception> {
        val isSelfMessage = userId == selfId

        val timestamp = relayClock.currentTime()

        val messageInfo = MessageInfo.newSent(message, timestamp, ttlMs)
        val conversationMessageInfo = ConversationMessageInfo(null, messageInfo.copy(
            isDelivered = isSelfMessage,
            receivedTimestamp = if (!isSelfMessage) 0 else relayClock.currentTime()
        ))

        val metadata = MessageMetadata(userId, null, MessageCategory.TEXT_SINGLE, messageInfo.id)

        return if (!isSelfMessage) {
            val m = TextMessage(MessageId(messageInfo.id), messageInfo.timestamp, message, null, ttlMs)
            val wrapper = SlyMessage.Text(m)

            val serialized = objectMapper.writeValueAsBytes(wrapper)

            messageSender.addToQueue(SenderMessageEntry(metadata, serialized)) bind {
                messageService.addMessage(userId.toConversationId(), conversationMessageInfo)
            }
        }
        else {
            //we don't actually wanna send a text message to ourselves; mostly because both the sent and received ids would be the same
            //so we just add a new sent message, then broadcast the sync message to other devices

            messageService.addMessage(userId.toConversationId(), conversationMessageInfo) bindUi {
                broadcastSentMessage(metadata, conversationMessageInfo) map { Unit }
            }
        }
    }

    /** Fetches group members for the given group and sends the given message to the MessageSender. */
    private fun sendMessageToGroup(groupId: GroupId, message: SlyMessage, messageCategory: MessageCategory, messageId: String? = null): Promise<Set<UserId>, Exception> {
        //we still send control messages to blocked contacts; however, we don't send text messages to them
        val getMembers = if (messageCategory == MessageCategory.TEXT_GROUP)
            groupService.getNonBlockedMembers(groupId)
        else
            groupService.getMembers(groupId)

        return getMembers bindUi { members ->
            if (members.isNotEmpty())
                sendMessageToMembers(groupId, members, message, messageCategory, messageId)
            else
                Promise.ofSuccess(emptySet())
        }
    }

    /** Send the message to all given members via the MessageSender. */
    private fun sendMessageToMembers(groupId: GroupId, members: Set<UserId>, message: SlyMessage, messageCategory: MessageCategory, messageId: String? = null): Promise<Set<UserId>, Exception> {
        val serialized = objectMapper.writeValueAsBytes(message)

        val messages = members.map {
            val metadata = MessageMetadata(it, groupId, messageCategory, messageId ?: randomUUID())
            SenderMessageEntry(metadata, serialized)
        }

        return messageSender.addToQueue(messages) map { members }
    }

    override fun sendGroupMessageTo(groupId: GroupId, message: String, ttlMs: Long): Promise<Unit, Exception> {
        val timestamp = relayClock.currentTime()

        val m = SlyMessage.Text(TextMessage(MessageId(randomMessageId()), timestamp, message, groupId, ttlMs))

        val messageId = randomUUID()

        return sendMessageToGroup(groupId, m, MessageCategory.TEXT_GROUP, messageId) bindUi {
            val messageInfo = MessageInfo.newSent(message, timestamp, ttlMs).copy(id = messageId)
            val conversationMessageInfo = ConversationMessageInfo(null, messageInfo)
            messageService.addMessage(groupId.toConversationId(), conversationMessageInfo)
        }
    }

    override fun createNewGroup(groupName: String, initialMembers: Set<UserId>): Promise<GroupId, Exception> {
        val groupId = GroupId(randomUUID())

        val groupInfo = GroupInfo(
            groupId,
            groupName,
            GroupMembershipLevel.JOINED
        )

        //include your other devices; this bypasses the issue where you get a SelfMessage and have no info on the group
        val allMembers = initialMembers + selfId

        val messages = allMembers.map {
            val members = HashSet(initialMembers)
            members.remove(it)

            val m = SlyMessage.GroupEvent(GroupEventMessage.Invitation(groupInfo.id, groupInfo.name, members))
            val serialized = objectMapper.writeValueAsBytes(m)

            val metadata = MessageMetadata(it, groupInfo.id, MessageCategory.OTHER, randomUUID())

            SenderMessageEntry(metadata, serialized)
        }

        return groupService.join(groupInfo, initialMembers) bindUi {
            messageSender.addToQueue(messages) map { groupId }
        }
    }

    private fun sendJoinToMembers(groupId: GroupId, members: Set<UserId>, newMembers: Set<UserId>): Promise<Unit, Exception> {
        //join messages are idempotent so don't bother checking for dups here
        val m = SlyMessage.GroupEvent(GroupEventMessage.Join(groupId, newMembers))

        return sendMessageToMembers(groupId, members, m, MessageCategory.OTHER) map { Unit }
    }

    private fun sendInvitationToNewMembers(groupInfo: GroupInfo, newMembers: Set<UserId>, members: Set<UserId>): Promise<Unit, Exception> {
        val groupId = groupInfo.id
        val invitation = SlyMessage.GroupEvent(GroupEventMessage.Invitation(groupId, groupInfo.name, members))

        return sendMessageToMembers(groupId, newMembers, invitation, MessageCategory.OTHER) bindUi {
            groupService.addMembers(groupId, newMembers)
        }
    }

    override fun inviteUsersToGroup(groupId: GroupId, newMembers: Set<UserId>): Promise<Unit, Exception> {
        return groupService.getInfo(groupId) bindUi { info ->
            if (info == null)
                throw IllegalStateException("Attempt to invite users to a non-existent group")

            groupService.getMembers(groupId) bindUi { members ->
                sendJoinToMembers(groupId, members, newMembers) bindUi {
                    sendInvitationToNewMembers(info, newMembers, members) bindUi {
                        groupService.addMembers(groupId, members)
                    }
                }
            }
        }
    }

    private fun sendPartMessagesTo(groupId: GroupId): Promise<Set<UserId>, Exception> {
        val message = SlyMessage.GroupEvent(GroupEventMessage.Part(groupId))

        return sendMessageToGroup(groupId, message, MessageCategory.OTHER)
    }

    override fun partGroup(groupId: GroupId): Promise<Boolean, Exception> {
        return sendPartMessagesTo(groupId) bindUi {
            groupService.part(groupId)
        }
    }

    override fun blockGroup(groupId: GroupId): Promise<Unit, Exception> {
        return sendPartMessagesTo(groupId) bindUi {
            groupService.block(groupId)
        }
    }

    override fun getLastMessagesFor(userId: UserId, startingAt: Int, count: Int): Promise<List<ConversationMessageInfo>, Exception> {
        return messageService.getLastMessages(userId.toConversationId(), startingAt, count)
    }

    override fun markConversationAsRead(userId: UserId): Promise<Unit, Exception> {
        return messageService.markConversationAsRead(userId.toConversationId())
    }

    /* Other */

    override fun addOfflineMessages(offlineMessages: List<Package>): Promise<Unit, Exception> {
        return processPackages(offlineMessages)
    }

    private fun sendSyncMessage(m: SyncMessage): Promise<Unit, Exception> {
        val serialized = objectMapper.writeValueAsBytes(SlyMessage.Sync(m))

        val metadata = MessageMetadata(selfId, null, MessageCategory.OTHER, randomUUID())
        return messageSender.addToQueue(SenderMessageEntry(metadata, serialized))
    }

    override fun broadcastNewDevice(deviceInfo: DeviceInfo): Promise<Unit, Exception> {
        return sendSyncMessage(SyncMessage.NewDevice(deviceInfo))
    }

    override fun broadcastMessageExpired(conversationId: ConversationId, messageId: String): Promise<Unit, Exception> {
        return sendSyncMessage(SyncMessage.MessageExpired(conversationId, MessageId(messageId)))
    }

    override fun broadcastMessagesRead(conversationId: ConversationId, messageIds: List<String>): Promise<Unit, Exception> {
        if (messageIds.isEmpty())
            return Promise.ofSuccess(Unit)

        return sendSyncMessage(SyncMessage.MessagesRead(conversationId, messageIds.map(::MessageId)))
    }

    override fun broadcastDeleted(conversationId: ConversationId, messageIds: List<String>): Promise<Unit, Exception> {
        if (messageIds.isEmpty())
            return Promise.ofSuccess(Unit)

        return sendSyncMessage(SyncMessage.MessagesDeleted(conversationId, messageIds.map(::MessageId)))
    }

    override fun broadcastDeletedAll(conversationId: ConversationId, lastMessageTimestamp: Long): Promise<Unit, Exception> {
        return sendSyncMessage(SyncMessage.MessagesDeletedAll(conversationId, lastMessageTimestamp))
    }

    override fun notifyContactAdd(userIds: Collection<UserId>): Promise<Unit, Exception> {
        val serialized = objectMapper.writeValueAsBytes(SlyMessage.Control(ControlMessage.WasAdded()))

        val messages = userIds.map {
            SenderMessageEntry(MessageMetadata(it, null, MessageCategory.OTHER, randomUUID()), serialized)
        }

        return messageSender.addToQueue(messages)
    }

    private fun broadcastSentMessage(metadata: MessageMetadata, conversationMessageInfo: ConversationMessageInfo?): Promise<MessageInfo?, Exception> {
        if (conversationMessageInfo == null)
            return Promise.ofSuccess(null)

        val recipient = if (metadata.groupId != null)
            ConversationId.Group(metadata.groupId)
        else
            ConversationId.User(metadata.userId)

        val messageInfo = conversationMessageInfo.info
        val sentMessageInfo = SyncSentMessageInfo(
            metadata.messageId,
            recipient,
            messageInfo.message,
            messageInfo.timestamp,
            messageInfo.receivedTimestamp,
            messageInfo.ttlMs
        )

        return sendSyncMessage(SyncMessage.SelfMessage(sentMessageInfo)) map { messageInfo }
    }

    override fun broadcastSync() {
        sendSyncMessage(SyncMessage.AddressBookSync()) fail {
            log.error("Failed to send SelfSync message: {}", it.message, it)
        }
    }
}
