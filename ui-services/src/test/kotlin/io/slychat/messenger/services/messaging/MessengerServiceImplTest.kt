package io.slychat.messenger.services.messaging

import com.fasterxml.jackson.databind.ObjectMapper
import com.nhaarman.mockito_kotlin.*
import io.slychat.messenger.core.*
import io.slychat.messenger.core.crypto.randomMessageId
import io.slychat.messenger.core.crypto.randomUUID
import io.slychat.messenger.core.persistence.*
import io.slychat.messenger.core.relay.ReceivedMessage
import io.slychat.messenger.core.relay.RelayClientEvent
import io.slychat.messenger.services.GroupService
import io.slychat.messenger.services.RelayClientManager
import io.slychat.messenger.services.RelayClock
import io.slychat.messenger.services.contacts.*
import io.slychat.messenger.services.crypto.EncryptedPackagePayloadV0
import io.slychat.messenger.testutils.KovenantTestModeRule
import io.slychat.messenger.testutils.thenAnswerWithArg
import io.slychat.messenger.testutils.thenResolve
import nl.komponents.kovenant.Promise
import org.assertj.core.api.Assertions.assertThat
import org.junit.Before
import org.junit.ClassRule
import org.junit.Test
import rx.subjects.PublishSubject
import java.util.*
import kotlin.test.assertEquals
import kotlin.test.assertTrue

//only downside of this api design is having to deserialize messages in tests
class MessengerServiceImplTest {
    companion object {
        @JvmField
        @ClassRule
        val kovenantTestMode = KovenantTestModeRule()

        val selfId = randomUserId()

        init {
            MockitoKotlin.registerInstanceCreator { randomGroupId() }
        }
    }

    val contactsService: ContactsService = mock()
    val addressBookOperationManager: AddressBookOperationManager = mock()
    val messageService: MessageService = mock()
    val groupService: GroupService = mock()
    val relayClientManager: RelayClientManager = mock()
    val messageSender: MessageSender = mock()
    val messageReceiver: MessageReceiver = mock()
    val relayClock: RelayClock = mock()

    val relayEvents: PublishSubject<RelayClientEvent> = PublishSubject.create()

    val syncEvents: PublishSubject<AddressBookSyncEvent> = PublishSubject.create()

    val messageSent: PublishSubject<MessageSendRecord> = PublishSubject.create()

    @Before
    fun before() {
        whenever(messageSender.messageSent).thenReturn(messageSent)

        whenever(relayClientManager.events).thenReturn(relayEvents)

        whenever(messageSender.addToQueue(any<SenderMessageEntry>())).thenResolve(Unit)
        whenever(messageSender.addToQueue(anyList())).thenResolve(Unit)

        //some useful defaults
        whenever(messageService.addMessage(any(), any())).thenAnswerWithArg(1)

        whenever(contactsService.addMissingContacts(any())).thenResolve(emptySet())
        whenever(messageReceiver.processPackages(any())).thenResolve(Unit)

        whenever(groupService.addMembers(any(), any())).thenResolve(Unit)
        whenever(groupService.join(any(), any())).thenResolve(Unit)
        whenever(groupService.part(any())).thenResolve(true)
        whenever(groupService.block(any())).thenResolve(Unit)
        whenever(groupService.getMembers(any())).thenResolve(emptySet())

        whenever(addressBookOperationManager.syncEvents).thenReturn(syncEvents)

        whenever(relayClock.currentTime()).thenAnswer { currentTimestamp() }
    }

    fun randomTextSingleRecord(): MessageSendRecord {
        return MessageSendRecord(
            randomTextSingleMetadata(),
            currentTimestamp()
        )
    }

    fun randomTextGroupRecord(): MessageSendRecord {
        return MessageSendRecord(
            randomTextGroupMetadata(),
            currentTimestamp()
        )
    }

    fun randomOtherRecord(): MessageSendRecord {
        return MessageSendRecord(
            randomOtherMetadata(),
            currentTimestamp()
        )
    }

    fun createService(): MessengerServiceImpl {
        return MessengerServiceImpl(
            contactsService,
            addressBookOperationManager,
            messageService,
            groupService,
            relayClientManager,
            messageSender,
            messageReceiver,
            relayClock,
            selfId
        )
    }

    fun wheneverAllowMessagesFrom(fn: (Set<UserId>) -> Promise<Set<UserId>, Exception>) {
        whenever(contactsService.filterBlocked(anySet())).thenAnswer {
            @Suppress("UNCHECKED_CAST")
            val a = it.arguments[0] as Set<UserId>
            fn(a)
        }
    }

    fun setAllowAllUsers() {
        wheneverAllowMessagesFrom {
            Promise.ofSuccess(it)
        }
    }

    fun setAllowedUsers(allowedUsers: Set<UserId>) {
        wheneverAllowMessagesFrom {
            Promise.ofSuccess<Set<UserId>, Exception>(allowedUsers)
        }
    }

    fun getQueuedPackages(): Collection<Package> {
        val argumentCaptor = argumentCaptor<List<Package>>()
        verify(messageReceiver).processPackages(capture(argumentCaptor))
        return argumentCaptor.value
    }

    fun newMessagePayload(message: String): String {
        val objectMapper = ObjectMapper()
        return objectMapper.writeValueAsString(EncryptedPackagePayloadV0(false, message.toByteArray()))
    }

    @Test
    fun `it should pass all offline messages to the receiver`() {
        val messengerService = createService()

        val sender = SlyAddress(UserId(1), 1)

        val packages = (0..1).map {
            Package(PackageId(sender, randomUUID()), currentTimestamp(), "payload")
        }

        setAllowAllUsers()

        messengerService.addOfflineMessages(packages)

        verify(messageReceiver).processPackages(packages)
    }

    @Test
    fun `it should drop blocked user offline packages`() {
        val messengerService = createService()

        val allowedSender = SlyAddress(UserId(1), 1)
        val blockedSender = SlyAddress(UserId(2), 1)

        val allowedPackage = Package(PackageId(allowedSender, randomUUID()), currentTimestamp(), "payload")

        val packages = listOf(
            allowedPackage,
            Package(PackageId(blockedSender, randomUUID()), currentTimestamp(), "payload")
        )

        setAllowedUsers(setOf(allowedSender.id))

        messengerService.addOfflineMessages(packages)

        val queued = getQueuedPackages()

        assertThat(queued)
            .containsOnly(allowedPackage)
            .`as`("Package list")
    }

    @Test
    fun `it should drop a relay package when ContactsService indicates a user is blocked`() {
        val messengerService = createService()

        val sender = SlyAddress(UserId(2), 1)

        val ev = ReceivedMessage(sender, "payload", "messageId", currentTimestamp())

        setAllowedUsers(emptySet())

        relayEvents.onNext(ev)

        val queued = getQueuedPackages()

        assertThat(queued)
            .`as`("Package list")
            .isEmpty()
    }

    @Test
    fun `it should add received relay messages to the package queue`() {
        val messengerService = createService()

        val sender = SlyAddress(UserId(2), 1)
        val messageId = randomUUID()
        val ev = ReceivedMessage(sender, newMessagePayload("payload"), messageId, currentTimestamp())

        setAllowAllUsers()

        relayEvents.onNext(ev)

        val queued = getQueuedPackages()

        assertThat(queued)
            .hasSize(1)
            .`as`("Package list")
    }

    @Test
    fun `it should send the relay an ack when a package has been queued after receiving it`() {
        val messengerService = createService()

        val sender = SlyAddress(UserId(2), 1)
        val messageId = randomUUID()
        val ev = ReceivedMessage(sender, newMessagePayload("payload"), messageId, currentTimestamp())

        setAllowAllUsers()

        relayEvents.onNext(ev)

        verify(relayClientManager).sendMessageReceivedAck(messageId)
    }

    @Test
    fun `it should queue a single text message to be sent when sendMessageTo is called`() {
        val messengerService = createService()

        val recipient = randomUserId()

        messengerService.sendMessageTo(recipient, randomMessageText(), 0)

        verify(messageSender).addToQueue(capture<SenderMessageEntry> {
            assertEquals(recipient, it.metadata.userId, "Invalid recipient")
            assertEquals(MessageCategory.TEXT_SINGLE, it.metadata.category, "Invalid category")
        })
    }

    fun deserializeTextMessage(bytes: ByteArray): TextMessage {
        val objectMapper = ObjectMapper()

        return objectMapper.readValue(bytes, SlyMessage.Text::class.java).m
    }

    @Test
    fun `it should use the RelayClock when creating sent message timestamps`() {
        val currentTime = 1000L

        whenever(relayClock.currentTime()).thenReturn(currentTime)

        val messengerService = createService()

        messengerService.sendMessageTo(randomUserId(), randomMessageText(), 0)

        verify(messageSender).addToQueue(capture<SenderMessageEntry> {
            val message = deserializeTextMessage(it.message)

            assertEquals(currentTime, message.timestamp, "RelayClock time not used")
        })
    }

    @Test
    fun `it should include the ttl in the generated TextMessage for a single convo`() {
        val messengerService = createService()

        val ttl = 1000L
        messengerService.sendMessageTo(randomUserId(), randomMessageText(), ttl)

        verify(messageSender).addToQueue(capture<SenderMessageEntry> {
            val message = deserializeTextMessage(it.message)
            assertEquals(ttl, message.ttl, "Invalid TTL")
        })
    }

    //also doubles as checking for mark as delivered
    @Test
    fun `it should emit a message updated event when receiving a message update for TEXT_SINGLE message`() {
        val messengerService = createService()

        val record = randomTextSingleRecord()
        val update = record.metadata
        val messageInfo = MessageInfo.newSent(update.messageId, 0).copy(isDelivered = true)
        val conversationMessageInfo = ConversationMessageInfo(null, messageInfo)

        val conversationId = update.userId.toConversationId()
        whenever(messageService.markMessageAsDelivered(any(), any(), any())).thenResolve(conversationMessageInfo)

        messageSent.onNext(record)

        verify(messageService).markMessageAsDelivered(conversationId, update.messageId, record.serverReceivedTimestamp)
    }

    @Test
    fun `it should emit a message updated event when receiving a message update for TEXT_GROUP message`() {
        val messengerService = createService()

        val record = randomTextGroupRecord()
        val update = record.metadata
        val messageInfo = MessageInfo.newSent(update.messageId, 0).copy(isDelivered = true)

        val conversationId = update.groupId!!.toConversationId()

        whenever(messageService.markMessageAsDelivered(any(), any(), any()))
            .thenResolve(ConversationMessageInfo(update.userId, messageInfo))

        messageSent.onNext(record)

        verify(messageService).markMessageAsDelivered(conversationId, update.messageId, record.serverReceivedTimestamp)
    }

    @Test
    fun `it should send the proper number of group messages when creating a group text message`() {
        val groupId = randomGroupId()
        val members = randomGroupMembers()

        val messengerService = createService()

        whenever(groupService.getNonBlockedMembers(groupId)).thenResolve(members)

        messengerService.sendGroupMessageTo(groupId, "msg", 0)

        verify(messageSender).addToQueue(capture<List<SenderMessageEntry>> {
            assertEquals(members, it.map { it.metadata.userId }.toSet(), "Member list is invalid")
        })
    }

    @Test
    fun `it should add to the message log but send nothing for groups with no more members`() {
        val groupId = randomGroupId()

        val messengerService = createService()

        whenever(groupService.getNonBlockedMembers(groupId)).thenResolve(emptySet())

        val message = "msg"
        messengerService.sendGroupMessageTo(groupId, message, 0)

        verify(messageService).addMessage(eq(groupId.toConversationId()), capture {
            assertEquals(message, it.info.message, "Message is invalid")
        })

        verify(messageSender, never()).addToQueue(any<List<SenderMessageEntry>>())
    }

    @Test
    fun `it should store only one message to the log when creating a group text message`() {
        val groupId = randomGroupId()
        val members = randomGroupMembers()

        val messengerService = createService()

        whenever(groupService.getNonBlockedMembers(groupId)).thenResolve(members)

        val message = "msg"

        messengerService.sendGroupMessageTo(groupId, message, 0)

        verify(messageService).addMessage(eq(groupId.toConversationId()), capture {
            assertEquals(message, it.info.message, "Text message doesn't match")
        })
    }

    @Test
    fun `it should send a group message with the same id as the message id in the database`() {
        val groupId = randomGroupId()
        val members = randomGroupMembers()

        val messengerService = createService()

        whenever(groupService.getNonBlockedMembers(groupId)).thenResolve(members)

        val message = "msg"

        messengerService.sendGroupMessageTo(groupId, message, 0)

        var sentMessageId: String? = null
        verify(messageSender).addToQueue(capture<List<SenderMessageEntry>> {
            sentMessageId = it.first().metadata.messageId
        })

        verify(messageService).addMessage(eq(groupId.toConversationId()), capture {
            assertEquals(sentMessageId!!, it.info.id, "Message IDs don't match")
        })
    }

    @Test
    fun `it should include the ttl in the generated TextMessage for a group convo`() {
        val messengerService = createService()

        val groupId = randomGroupId()

        whenever(groupService.getNonBlockedMembers(groupId)).thenResolve(setOf(randomUserId()))

        val ttl = 1000L
        messengerService.sendGroupMessageTo(groupId, randomMessageText(), ttl)

        verify(messageSender).addToQueue(capture<List<SenderMessageEntry>> {
            val m = it.first()
            val message = deserializeTextMessage(m.message)
            assertEquals(ttl, message.ttl, "Invalid TTL")
        })
    }

    @Test
    fun `partGroup should part the given group`() {
        val messengerService = createService()

        val groupId = randomGroupId()

        whenever(groupService.getMembers(groupId)).thenResolve(randomUserIds())

        messengerService.partGroup(groupId).get()

        verify(groupService).part(groupId)
    }

    fun assertPartMessagesSent(members: Set<UserId>) {
        verify(messageSender).addToQueue(capture<List<SenderMessageEntry>> {
            assertEquals(members, it.mapToSet { it.metadata.userId }, "Invalid users")
            val messages = convertMessageFromSerialized<SlyMessage.GroupEvent>(it)
            messages.forEach {
                assertTrue(it.m is GroupEventMessage.Part, "Invalid message type")
            }
        })
    }

    @Test
    fun `partGroup should queue part messages to all members`() {
        val messengerService = createService()

        val groupId = randomGroupId()
        val members = randomUserIds()

        whenever(groupService.getMembers(groupId)).thenResolve(members)

        messengerService.partGroup(groupId).get()

        assertPartMessagesSent(members)
    }

    @Test
    fun `partGroup should not queue any messages when no members remain in the group`() {
        val messengerService = createService()

        val groupId = randomGroupId()

        whenever(groupService.getMembers(groupId)).thenResolve(emptySet())

        messengerService.partGroup(groupId).get()

        verify(messageSender, never()).addToQueue(any<List<SenderMessageEntry>>())
    }

    @Test
    fun `blockGroup should add the group to the block list`() {
        val messengerService = createService()

        val groupId = randomGroupId()

        messengerService.blockGroup(groupId).get()

        verify(groupService).block(groupId)
    }

    @Test
    fun `blockGroup should queue part messages to all members`() {
        val messengerService = createService()

        val groupId = randomGroupId()
        val members = randomUserIds()

        whenever(groupService.getMembers(groupId)).thenResolve(members)

        messengerService.blockGroup(groupId).get()

        assertPartMessagesSent(members)
    }

    @Test
    fun `blockGroup should not queue messages when no members remain in the group`() {
        val messengerService = createService()

        val groupId = randomGroupId()

        whenever(groupService.getMembers(groupId)).thenResolve(emptySet())

        messengerService.blockGroup(groupId).get()

        verify(messageSender, never()).addToQueue(any<List<SenderMessageEntry>>())
    }

    fun getAllSentMessages(times: Int): List<SenderMessageEntry> {
        val captor = argumentCaptor<List<SenderMessageEntry>>()
        verify(messageSender, atLeast(times)).addToQueue(capture(captor))

        return captor.allValues.flatten()
    }

    inline fun <reified T : GroupEventMessage> assertNoGroupMessagesSent() {
        val messages = getAllSentMessages(0)

        if (messages.isEmpty())
            return

        messages.forEach {
            val recipient = it.metadata.userId

            val wrapper = convertMessageFromSerialized<SlyMessage.GroupEvent>(it)

            if (wrapper.m is T)
                throw AssertionError("Unexpected ${T::class.simpleName} message to $recipient")
        }
    }

    inline fun <reified T : SyncMessage> retrieveSyncMessage(): T {
        val captor = argumentCaptor<SenderMessageEntry>()
        verify(messageSender, atLeast(1)).addToQueue(capture(captor))

        val wrapper = convertMessageFromSerialized<SlyMessage.Sync>(captor.value)

        return wrapper.m as? T ?: throw AssertionError("Unexpected ${T::class.simpleName} message")
    }

    inline fun <reified T : ControlMessage> convertToControlMessage(entry: SenderMessageEntry): T {
        val wrapper = convertMessageFromSerialized<SlyMessage.Control>(entry)

        return wrapper.m as? T ?: throw AssertionError("Unexpected ${T::class.simpleName} message")
    }

    /** Assert that the given group message type was sent to everyone in the given list, and that it satisifies certain conditions. */
    inline fun <reified T> assertGroupMessagesSentTo(expectedConversationIds: Set<UserId>, asserter: (UserId, T) -> Unit) {
        val messages = getAllSentMessages(1)

        assertTrue(messages.isNotEmpty(), "No messages were sent")

        val sendTo = HashMap<UserId, Boolean>()
        sendTo.putAll(expectedConversationIds.map { it to false })

        messages.forEach {
            val recipient = it.metadata.userId

            val wrapper = convertMessageFromSerialized<SlyMessage.GroupEvent>(it)

            val m = wrapper.m as? T

            if (m != null) {
                asserter(recipient, m)

                assertTrue(recipient in sendTo, "Unexpected recipient")
                sendTo[recipient] = true
            }
        }

        val missing = HashSet(expectedConversationIds)
        missing.removeAll(sendTo.filterValues { it }.keys)

        if (missing.isNotEmpty())
            throw AssertionError("Missing recipients: $missing")
    }

    /** Runs the body with the proper setup for an existing group to be tested. */
    fun withExistingGroup(noMembers: Boolean = false, body: (messengerService: MessengerServiceImpl, groupInfo: GroupInfo, members: Set<UserId>) -> Unit) {
        val messengerService = createService()

        val groupInfo = randomGroupInfo()
        val groupId = groupInfo.id
        val members = if (noMembers) emptySet() else randomUserIds()

        whenever(groupService.getInfo(groupId)).thenResolve(groupInfo)
        whenever(groupService.getMembers(groupId)).thenResolve(members)

        body(messengerService, groupInfo, members)
    }

    @Test
    fun `inviteUsersToGroup should handle not having an existing members in the group`() {
        withExistingGroup(true) { messengerService, groupInfo, members ->
            assertNoGroupMessagesSent<GroupEventMessage.Join>()
        }
    }

    @Test
    fun `inviteUsersToGroup should send a join message to each current member`() {
        withExistingGroup { messengerService, groupInfo, members ->
            val newMembers = randomUserIds()

            messengerService.inviteUsersToGroup(groupInfo.id, newMembers).get()

            assertGroupMessagesSentTo<GroupEventMessage.Join>(members) { recipient, m ->
                assertEquals(newMembers, m.joined, "Joined member list is incorrect")
            }
        }
    }

    @Test
    fun `inviteUsersToGroup should send an invitation message to each new member`() {
        withExistingGroup { messengerService, groupInfo, members ->
            val newMembers = randomUserIds()

            messengerService.inviteUsersToGroup(groupInfo.id, newMembers).get()

            assertGroupMessagesSentTo<GroupEventMessage.Invitation>(newMembers) { recipient, m ->
                assertEquals(groupInfo.name, m.name, "Invalid group name")
                assertEquals(members, m.members, "Joined member list is incorrect")
            }
        }
    }

    @Test
    fun `inviteUsersToGroup should add new members to the group member list`() {
        withExistingGroup { messengerService, groupInfo, members ->
            val newMembers = randomUserIds()

            messengerService.inviteUsersToGroup(groupInfo.id, newMembers).get()

            verify(groupService).addMembers(groupInfo.id, newMembers)
        }
    }

    @Test
    fun `createNewGroup should sent invitations to each initial member and yourself`() {
        val groupName = randomGroupName()
        val initialMembers = randomUserIds()

        val messengerService = createService()

        messengerService.createNewGroup(groupName, initialMembers).get()

        val allMembers = initialMembers + selfId

        assertGroupMessagesSentTo<GroupEventMessage.Invitation>(allMembers) { recipient, m ->
            val expectedMembers = HashSet(initialMembers)
            expectedMembers.remove(recipient)

            assertEquals(expectedMembers, m.members, "Invalid members list")
            assertEquals(groupName, m.name, "Invalid group name")
        }
    }

    @Test
    fun `createNewGroup should not sent invitations to anyone but yourself if given no initial members`() {
        val groupName = randomGroupName()

        val messengerService = createService()

        messengerService.createNewGroup(groupName, emptySet()).get()

        assertGroupMessagesSentTo<GroupEventMessage.Invitation>(setOf(selfId)) { recipient, m ->
            assertThat(m.members).isEmpty()
        }
    }

    @Test
    fun `createNewGroup should store the new group data`() {
        val groupName = randomGroupName()
        val initialMembers = randomUserIds()

        val messengerService = createService()

        messengerService.createNewGroup(groupName, initialMembers).get()

        verify(groupService).join(capture {
            assertEquals(groupName, it.name, "Invalid group name")
            assertEquals(GroupMembershipLevel.JOINED, it.membershipLevel, "Invalid membership level")
        }, eq(initialMembers))
    }

    //since send queue has a fk for group ids
    @Test
    fun `createNewGroup should create group data before storing messages`() {
        val groupName = randomGroupName()
        val initialMembers = randomUserIds()

        val messengerService = createService()

        messengerService.createNewGroup(groupName, initialMembers).get()

        val order = inOrder(groupService, messageSender)

        order.verify(groupService).join(any(), any())
        order.verify(messageSender).addToQueue(anyList())
    }

    @Test
    fun `broadcastNewDevice should send a new device message to your own devices`() {
        val messengerService = createService()

        val deviceInfo = randomDeviceInfo()

        messengerService.broadcastNewDevice(deviceInfo).get()

        val message = retrieveSyncMessage<SyncMessage.NewDevice>()

        assertEquals(SyncMessage.NewDevice(deviceInfo), message, "Invalid sync message")
    }

    @Test
    fun `when receiving a sent notification for a text single message, it should send itself a sync message`() {
        val messengerService = createService()

        val messageInfo = randomSentMessageInfo()
        val conversationMessageInfo = ConversationMessageInfo(null, messageInfo)
        val record = randomTextSingleRecord()
        val metadata = record.metadata

        whenever(messageService.markMessageAsDelivered(metadata.userId.toConversationId(), metadata.messageId, record.serverReceivedTimestamp)).thenResolve(conversationMessageInfo)

        messageSent.onNext(record)

        val expected = SyncSentMessageInfo(
            metadata.messageId,
            ConversationId.User(metadata.userId),
            messageInfo.message,
            messageInfo.timestamp,
            messageInfo.receivedTimestamp,
            messageInfo.ttl
        )

        val message = retrieveSyncMessage<SyncMessage.SelfMessage>()
        assertEquals(expected, message.sentMessageInfo, "Invalid sent message info")
    }

    @Test
    fun `when receiving a sent notification for a text group message, it should send itself a sync message if the message was not marked as delivered`() {
        val messengerService = createService()

        val messageInfo = randomSentMessageInfo()
        val record = randomTextGroupRecord()
        val metadata = record.metadata
        val groupId = metadata.groupId!!

        val groupMessageInfo = ConversationMessageInfo(null, messageInfo)
        whenever(messageService.markMessageAsDelivered(groupId.toConversationId(), metadata.messageId, record.serverReceivedTimestamp)).thenResolve(groupMessageInfo)

        messageSent.onNext(record)

        val expected = SyncSentMessageInfo(
            metadata.messageId,
            ConversationId.Group(groupId),
            messageInfo.message,
            messageInfo.timestamp,
            messageInfo.receivedTimestamp,
            messageInfo.ttl
        )

        val message = retrieveSyncMessage<SyncMessage.SelfMessage>()
        assertEquals(expected, message.sentMessageInfo, "Invalid sent message info")
    }

    @Test
    fun `when receiving a sent notification for a text group message, it should not send itself a sync message if the message was already marked as delivered`() {
        val messengerService = createService()

        val record = randomTextGroupRecord()
        val metadata = record.metadata
        val groupId = metadata.groupId!!

        whenever(messageService.markMessageAsDelivered(groupId.toConversationId(), metadata.messageId, record.serverReceivedTimestamp)).thenResolve(null)

        messageSent.onNext(record)

        verify(messageSender, never()).addToQueue(any<SenderMessageEntry>())
    }

    @Test
    fun `when receiving a notification for an other category message, it should not send itself a sync message`() {
        val messengerService = createService()

        messageSent.onNext(randomOtherRecord())

        verify(messageSender, never()).addToQueue(any<SenderMessageEntry>())
    }

    @Test
    fun `it should send itself a SelfAddressBookUpdated message when a sync job completes with a non-zero updateCount`() {
        val messengerService = createService()

        val info = AddressBookSyncJobInfo(true, true, true)
        val result = AddressBookSyncResult(true, 1, false)

        syncEvents.onNext(AddressBookSyncEvent.End(info, result))

        retrieveSyncMessage<SyncMessage.AddressBookSync>()
    }

    @Test
    fun `it should not send itself a SelfAddressBookUpdated message when a sync job completes with a zero updateCount`() {
        val messengerService = createService()

        val info = AddressBookSyncJobInfo(true, true, true)
        val result = AddressBookSyncResult(true, 0, false)

        syncEvents.onNext(AddressBookSyncEvent.End(info, result))

        verify(messageSender, never()).addToQueue(any<SenderMessageEntry>())
    }

    @Test
    fun `it should send a WasAdded message when notifyContactAdd is called`() {
        val messengerService = createService()
        val userId = randomUserId()

        messengerService.notifyContactAdd(setOf(userId)).get()

        getAllSentMessages(1).forEach {
            assertEquals(userId, it.metadata.userId, "Invalid recipient id")
            convertToControlMessage<ControlMessage.WasAdded>(it)
        }
    }

    @Test
    fun `it should generate a MessageExpired sync message when broadcastMessageExpired is called`() {
        val messengerService = createService()

        val conversationId = randomUserConversationId()
        val messageId = randomMessageId()
        messengerService.broadcastMessageExpired(conversationId, messageId).get()

        val message = retrieveSyncMessage<SyncMessage.MessageExpired>()

        assertEquals(SyncMessage.MessageExpired(conversationId, MessageId(messageId)), message, "Invalid sync message")
    }
}