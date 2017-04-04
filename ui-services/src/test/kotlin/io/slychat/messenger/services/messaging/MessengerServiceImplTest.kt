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
import io.slychat.messenger.services.contacts.ContactsService
import io.slychat.messenger.services.crypto.EncryptedPackagePayloadV0
import io.slychat.messenger.services.files.StorageService
import io.slychat.messenger.testutils.KovenantTestModeRule
import io.slychat.messenger.testutils.desc
import io.slychat.messenger.testutils.thenResolve
import io.slychat.messenger.testutils.thenResolveUnit
import nl.komponents.kovenant.Promise
import org.assertj.core.api.Assertions.assertThat
import org.junit.Before
import org.junit.ClassRule
import org.junit.Test
import rx.subjects.PublishSubject
import java.util.*
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

//only downside of this api design is having to deserialize messages in tests
class MessengerServiceImplTest {
    companion object {
        @JvmField
        @ClassRule
        val kovenantTestMode = KovenantTestModeRule()

        private val selfId = randomUserId()

        init {
            MockitoKotlin.registerInstanceCreator { randomGroupId() }
        }
    }

    private val contactsService: ContactsService = mock()
    private val messageService: MessageService = mock()
    private val groupService: GroupService = mock()
    private val storageService: StorageService = mock()
    private val relayClientManager: RelayClientManager = mock()
    private val messageSender: MessageSender = mock()
    private val messageReceiver: MessageReceiver = mock()
    private val relayClock: RelayClock = mock()

    private val relayEvents: PublishSubject<RelayClientEvent> = PublishSubject.create()

    private val messageSent: PublishSubject<MessageSendRecord> = PublishSubject.create()

    @Before
    fun before() {
        whenever(messageSender.messageSent).thenReturn(messageSent)

        whenever(relayClientManager.events).thenReturn(relayEvents)

        whenever(messageSender.addToQueue(any<SenderMessageEntry>())).thenResolve(Unit)
        whenever(messageSender.addToQueue(anyList())).thenResolve(Unit)

        //some useful defaults
        whenever(messageService.addMessage(any(), any())).thenResolveUnit()

        whenever(contactsService.addMissingContacts(any())).thenResolve(emptySet())
        whenever(messageReceiver.processPackages(any())).thenResolve(Unit)

        whenever(groupService.addMembers(any(), any())).thenResolve(Unit)
        whenever(groupService.join(any(), any())).thenResolve(Unit)
        whenever(groupService.part(any())).thenResolve(true)
        whenever(groupService.block(any())).thenResolve(Unit)
        whenever(groupService.getMembers(any())).thenResolve(emptySet())

        whenever(relayClock.currentTime()).thenReturn(1)
    }

    private fun randomTextSingleRecord(): MessageSendRecord.Ok {
        return MessageSendRecord.Ok(
            randomTextSingleMetadata(),
            currentTimestamp()
        )
    }

    private fun randomTextGroupRecord(): MessageSendRecord.Ok {
        return MessageSendRecord.Ok(
            randomTextGroupMetadata(),
            currentTimestamp()
        )
    }

    private fun randomOtherRecord(): MessageSendRecord.Ok {
        return MessageSendRecord.Ok(
            randomOtherMetadata(),
            currentTimestamp()
        )
    }

    private fun createService(): MessengerServiceImpl {
        return MessengerServiceImpl(
            contactsService,
            messageService,
            groupService,
            storageService,
            relayClientManager,
            messageSender,
            messageReceiver,
            relayClock,
            selfId
        )
    }

    private fun wheneverAllowMessagesFrom(fn: (Set<UserId>) -> Promise<Set<UserId>, Exception>) {
        whenever(contactsService.filterBlocked(anySet())).thenAnswer {
            @Suppress("UNCHECKED_CAST")
            val a = it.arguments[0] as Set<UserId>
            fn(a)
        }
    }

    private fun setAllowAllUsers() {
        wheneverAllowMessagesFrom {
            Promise.ofSuccess(it)
        }
    }

    private fun setAllowedUsers(allowedUsers: Set<UserId>) {
        wheneverAllowMessagesFrom {
            Promise.ofSuccess<Set<UserId>, Exception>(allowedUsers)
        }
    }

    private fun getQueuedPackages(): Collection<Package> {
        val argumentCaptor = argumentCaptor<List<Package>>()
        verify(messageReceiver).processPackages(capture(argumentCaptor))
        return argumentCaptor.value
    }

    private fun newMessagePayload(message: String): String {
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

        messengerService.sendMessageTo(recipient, randomMessageText(), 0, emptyList())

        verify(messageSender).addToQueue(capture<SenderMessageEntry> {
            assertEquals(recipient, it.metadata.userId, "Invalid recipient")
            assertEquals(MessageCategory.TEXT_SINGLE, it.metadata.category, "Invalid category")
        })
    }

    @Test
    fun `it should add an undelivered sent message when sendMessageTo is called`() {
        val messengerService = createService()

        val userId = randomUserId()

        messengerService.sendMessageTo(userId, randomMessageText(), 0, emptyList())

        verify(messageService).addMessage(eq(userId.toConversationId()), capture {
            assertFalse(it.info.isDelivered, "Should not be marked as delivered")
            assertEquals(0, it.info.receivedTimestamp, "Received timestamp should not be set")
        })
    }

    @Test
    fun `it should add an undelivered sent message when sendGroupMessageTo is called`() {
        val messengerService = createService()

        val groupId = randomGroupId()

        whenever(groupService.getNonBlockedMembers(groupId)).thenResolve(setOf(randomUserId()))
        
        messengerService.sendGroupMessageTo(groupId, randomMessageText(), 0, emptyList())

        verify(messageService).addMessage(eq(groupId.toConversationId()), capture {
            assertFalse(it.info.isDelivered, "Should not be marked as delivered")
            assertEquals(0, it.info.receivedTimestamp, "Received timestamp should not be set")
        })
    }

    private fun deserializeTextMessage(bytes: ByteArray): TextMessage {
        val objectMapper = ObjectMapper()

        return objectMapper.readValue(bytes, SlyMessage.Text::class.java).m
    }

    @Test
    fun `it should use the RelayClock when creating sent message timestamps for user messages`() {
        val currentTime = 1000L

        whenever(relayClock.currentTime()).thenReturn(currentTime)

        val messengerService = createService()

        val userId = randomUserId()

        messengerService.sendMessageTo(userId, randomMessageText(), 0, emptyList())

        verify(messageSender).addToQueue(capture<SenderMessageEntry> {
            val message = deserializeTextMessage(it.message)

            assertEquals(currentTime, message.timestamp, "RelayClock time not used")
        })

        verify(messageService).addMessage(eq(userId.toConversationId()), capture {
            assertEquals(currentTime, it.info.timestamp, "RelayClock time not used")
        })
    }

    @Test
    fun `it should use the RelayClock when creating sent message timestamps for group messages`() {
        val currentTime = 1000L

        whenever(relayClock.currentTime()).thenReturn(currentTime)

        val messengerService = createService()

        val groupId = randomGroupId()

        whenever(groupService.getNonBlockedMembers(groupId)).thenResolve(setOf(randomUserId()))

        messengerService.sendGroupMessageTo(groupId, randomMessageText(), 0, emptyList())

        verify(messageSender).addToQueue(capture<List<SenderMessageEntry>> {
            it.forEach {
                val message = deserializeTextMessage(it.message)

                assertEquals(currentTime, message.timestamp, "RelayClock time not used")
            }
        })

        verify(messageService).addMessage(eq(groupId.toConversationId()), capture {
            assertEquals(currentTime, it.info.timestamp, "RelayClock time not used")
        })
    }

    @Test
    fun `it should include the ttl in the generated TextMessage for a single convo`() {
        val messengerService = createService()

        val ttl = 1000L
        messengerService.sendMessageTo(randomUserId(), randomMessageText(), ttl, emptyList())

        verify(messageSender).addToQueue(capture<SenderMessageEntry> {
            val message = deserializeTextMessage(it.message)
            assertEquals(ttl, message.ttlMs, "Invalid TTL")
        })
    }

    @Test
    fun `sendMessageTo should include the ttl when adding the message`() {
        val messengerService = createService()

        val ttl = 1000L
        val userId = randomUserId()
        messengerService.sendMessageTo(userId, randomMessageText(), ttl, emptyList())

        verify(messageService).addMessage(eq(userId.toConversationId()), capture {
            assertEquals(ttl, it.info.ttlMs, "Invalid TTL value")
        })
    }

    @Test
    fun `sendGroupMessageTo should include the ttl when adding the message`() {
        val messengerService = createService()

        val ttl = 1000L
        val groupId = randomGroupId()

        whenever(groupService.getNonBlockedMembers(groupId)).thenResolve(setOf(randomUserId()))

        messengerService.sendGroupMessageTo(groupId, randomMessageText(), ttl, emptyList())

        verify(messageService).addMessage(eq(groupId.toConversationId()), capture {
            assertEquals(ttl, it.info.ttlMs, "Invalid TTL value")
        })
    }

    @Test
    fun `it should include the ttl in the generated TextMessage for a group convo`() {
        val messengerService = createService()

        val groupId = randomGroupId()

        whenever(groupService.getNonBlockedMembers(groupId)).thenResolve(setOf(randomUserId()))

        val ttl = 1000L
        messengerService.sendGroupMessageTo(groupId, randomMessageText(), ttl, emptyList())

        verify(messageSender).addToQueue(capture<List<SenderMessageEntry>> {
            val m = it.first()
            val message = deserializeTextMessage(m.message)
            assertEquals(ttl, message.ttlMs, "Invalid TTL")
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

        val conversationId = update.getConversationId()
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

        val conversationId = update.getConversationId()

        whenever(messageService.markMessageAsDelivered(any(), any(), any()))
            .thenResolve(ConversationMessageInfo(update.userId, messageInfo))

        messageSent.onNext(record)

        verify(messageService).markMessageAsDelivered(conversationId, update.messageId, record.serverReceivedTimestamp)
    }

    @Test
    fun `it should call MessageService addFailures when receiving a MessageSendRecord Failure`() {
        val messengerService = createService()
        val metadata = randomTextSingleMetadata()
        val failure = MessageSendFailure.InactiveUser()
        val record = MessageSendRecord.Failure(metadata, failure)
        val failures = mapOf(
            metadata.userId to failure
        )

        whenever(messageService.addFailures(any(), any(), any())).thenResolveUnit()

        messageSent.onNext(record)

        verify(messageService).addFailures(metadata.getConversationId(), metadata.messageId, failures)
    }

    @Test
    fun `it should send the proper number of group messages when creating a group text message`() {
        val groupId = randomGroupId()
        val members = randomGroupMembers()

        val messengerService = createService()

        whenever(groupService.getNonBlockedMembers(groupId)).thenResolve(members)

        messengerService.sendGroupMessageTo(groupId, "msg", 0, emptyList())

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
        messengerService.sendGroupMessageTo(groupId, message, 0, emptyList())

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

        messengerService.sendGroupMessageTo(groupId, message, 0, emptyList())

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

        messengerService.sendGroupMessageTo(groupId, message, 0, emptyList())

        var sentMessageId: String? = null
        verify(messageSender).addToQueue(capture<List<SenderMessageEntry>> {
            sentMessageId = it.first().metadata.messageId
        })

        verify(messageService).addMessage(eq(groupId.toConversationId()), capture {
            assertEquals(sentMessageId!!, it.info.id, "Message IDs don't match")
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

    private fun assertPartMessagesSent(members: Set<UserId>) {
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

    private fun getAllSentMessages(times: Int): List<SenderMessageEntry> {
        val captor = argumentCaptor<List<SenderMessageEntry>>()
        verify(messageSender, atLeast(times)).addToQueue(capture(captor))

        return captor.allValues.flatten()
    }

    private inline fun <reified T : GroupEventMessage> assertNoGroupMessagesSent() {
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

    private fun getSentTextMessage(): TextMessage {
        val captor = argumentCaptor<SenderMessageEntry>()

        verify(messageSender).addToQueue(capture(captor))

        val m = captor.value
        return deserializeTextMessage(m.message)
    }

    private inline fun <reified T : SyncMessage> retrieveSyncMessage(): T {
        val captor = argumentCaptor<SenderMessageEntry>()
        verify(messageSender, atLeast(1)).addToQueue(capture(captor))

        val wrapper = convertMessageFromSerialized<SlyMessage.Sync>(captor.value)

        return wrapper.m as? T ?: throw AssertionError("Unexpected ${T::class.simpleName} message")
    }

    private inline fun <reified T : ControlMessage> convertToControlMessage(entry: SenderMessageEntry): T {
        val wrapper = convertMessageFromSerialized<SlyMessage.Control>(entry)

        return wrapper.m as? T ?: throw AssertionError("Unexpected ${T::class.simpleName} message")
    }

    /** Assert that the given group message type was sent to everyone in the given list, and that it satisifies certain conditions. */
    private inline fun <reified T> assertGroupMessagesSentTo(expectedConversationIds: Set<UserId>, asserter: (UserId, T) -> Unit) {
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
    private fun withExistingGroup(noMembers: Boolean = false, body: (messengerService: MessengerServiceImpl, groupInfo: GroupInfo, members: Set<UserId>) -> Unit) {
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
            messageInfo.ttlMs
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
            messageInfo.ttlMs
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
    fun `it should send itself a SelfAddressBookUpdated message when broadcastSync is called`() {
        val messengerService = createService()

        messengerService.broadcastAddressBookSync()

        retrieveSyncMessage<SyncMessage.AddressBookSync>()
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

    @Test
    fun `it should not generate a MessagesRead message when broadcastMessagesRead is called with an empty list`() {
        val messengerService = createService()

        messengerService.broadcastMessagesRead(randomGroupConversationId(), emptyList()).get()

        verify(messageSender, never()).addToQueue(any<SenderMessageEntry>())
    }

    @Test
    fun `it should generate a MessagesRead message when broadcastMessagesRead is called with a non-empty list`() {
        val messengerService = createService()

        val conversationId = randomGroupConversationId()
        val messageIds = randomMessageIds()

        messengerService.broadcastMessagesRead(conversationId, messageIds).get()

        val message = retrieveSyncMessage<SyncMessage.MessagesRead>()

        assertEquals(SyncMessage.MessagesRead(conversationId, messageIds.map(::MessageId)), message, "Invalid sync message")
    }

    @Test
    fun `it should send a text message to yourself when sendMessageTo is called for yourself`() {
        val messengerService = createService()

        val recipient = selfId

        messengerService.sendMessageTo(recipient, randomMessageText(), 0, emptyList())

        verify(messageSender).addToQueue(capture<SenderMessageEntry> {
            assertNotEquals(MessageCategory.TEXT_SINGLE, it.metadata.category, "Invalid category")
        })
    }

    @Test
    fun `it should add a new already-delivered message to yourself when sendMessageTo is called for yourself`() {
        val messengerService = createService()

        val recipient = selfId

        val messageText = randomMessageText()

        messengerService.sendMessageTo(recipient, messageText, 0, emptyList())

        verify(messageService).addMessage(eq(recipient.toConversationId()), capture {
            assertTrue(it.info.isDelivered, "Not marked as delivered")
            assertEquals(messageText, it.info.message, "Invalid message")
            assertTrue(it.info.isSent, "Not marked as sent")
            assertEquals(it.info.receivedTimestamp, relayClock.currentTime(), "Invalid received timestamp")
        })
    }

    @Test
    fun `it should broadcast a sent message message to yourself when sendMessageTo is called for yourself`() {
        val messengerService = createService()

        val recipient = selfId

        val messageText = randomMessageText()

        messengerService.sendMessageTo(recipient, messageText, 0, emptyList())

        val selfMessage = retrieveSyncMessage<SyncMessage.SelfMessage>()

        assertEquals(messageText, selfMessage.sentMessageInfo.message, "Invalid message text")
    }

    @Test
    fun `it should generate a Deleted message when broadcastDeleted is called with a non-empty list`() {
        val messengerService = createService()

        val conversationId = randomGroupConversationId()
        val messageIds = randomMessageIds()

        messengerService.broadcastDeleted(conversationId, messageIds).get()

        val message = retrieveSyncMessage<SyncMessage.MessagesDeleted>()

        assertEquals(SyncMessage.MessagesDeleted(conversationId, messageIds.map(::MessageId)), message, "Invalid sync message")
    }

    @Test
    fun `it should generate a Deleted message when broadcastDeleted is called with an empty list`() {
        val messengerService = createService()

        messengerService.broadcastDeleted(randomGroupConversationId(), emptyList()).get()

        verify(messageSender, never()).addToQueue(any<SenderMessageEntry>())
    }

    @Test
    fun `it should generate a DeletedAll message when broadcastDeletedAll is called`() {
        val messengerService = createService()

        val conversationId = randomGroupConversationId()
        val lastMessageTimestamp = 10L

        messengerService.broadcastDeletedAll(conversationId, lastMessageTimestamp).get()

        val message = retrieveSyncMessage<SyncMessage.MessagesDeletedAll>()

        assertEquals(SyncMessage.MessagesDeletedAll(conversationId, lastMessageTimestamp), message, "Invalid sync message")
    }

    @Test
    fun `it should generate a FileListSync message when broadcastFileListSync is called`() {
        val messengerService = createService()

        messengerService.broadcastFileListSync()

        val message = retrieveSyncMessage<SyncMessage.FileListSync>()

        assertEquals(SyncMessage.FileListSync(), message, "Invalid sync message")
    }

    @Test
    fun `it should add attachment info to stored messages for remote source attachments if present`() {
        val service = createService()

        val file = randomRemoteFile()
        whenever(storageService.getFilesById(listOf(file.id))).thenResolve(mapOf(file.id to file))

        val expected = MessageAttachmentInfo(
            0,
            file.userMetadata.fileName,
            file.id,
            false
        )

        val attachment = AttachmentSource.Remote(file.id, false)
        val userId = randomUserId()
        service.sendMessageTo(userId, "test", 0, listOf(attachment)).get()

        verify(messageService).addMessage(any(), capture {
            assertThat(it.info.attachments).desc("Should contain a valid attachment description") {
                containsOnly(expected)
            }
        })
    }

    @Test
    fun `it should include attachment info for queued messages if present`() {
        val service = createService()

        val file = randomRemoteFile()
        whenever(storageService.getFilesById(listOf(file.id))).thenResolve(mapOf(file.id to file))

        val expected = TextMessageAttachment(
            file.id,
            file.shareKey,
            file.userMetadata.fileName,
            file.userMetadata.fileKey
        )

        val attachment = AttachmentSource.Remote(file.id, false)
        val userId = randomUserId()
        service.sendMessageTo(userId, "test", 0, listOf(attachment)).get()

        val m = getSentTextMessage()

        assertThat(m.attachments).desc("Should contain a valid attachment") {
            containsOnly(expected)
        }
    }

    @Test
    fun `it should include group message attachment info for remote source attachments if present`() {
        TODO()
    }
}