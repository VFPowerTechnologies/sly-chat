package io.slychat.messenger.services.messaging

import com.fasterxml.jackson.databind.ObjectMapper
import com.nhaarman.mockito_kotlin.*
import io.slychat.messenger.core.*
import io.slychat.messenger.core.persistence.*
import io.slychat.messenger.core.relay.ReceivedMessage
import io.slychat.messenger.core.relay.RelayClientEvent
import io.slychat.messenger.services.RelayClientManager
import io.slychat.messenger.services.assertEventEmitted
import io.slychat.messenger.services.contacts.ContactEvent
import io.slychat.messenger.services.contacts.ContactsService
import io.slychat.messenger.services.crypto.EncryptedPackagePayloadV0
import io.slychat.messenger.testutils.KovenantTestModeRule
import io.slychat.messenger.testutils.testSubscriber
import io.slychat.messenger.testutils.thenReturn
import nl.komponents.kovenant.Promise
import org.assertj.core.api.Assertions.assertThat
import org.junit.ClassRule
import org.junit.Test
import rx.subjects.PublishSubject
import java.util.*
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class MessengerServiceImplTest {
    companion object {
        @JvmField
        @ClassRule
        val kovenantTestMode = KovenantTestModeRule()

        val selfId = randomUserId()
    }

    val contactsService: ContactsService = mock()
    val messagePersistenceManager: MessagePersistenceManager = mock()
    val groupPersistenceManager: GroupPersistenceManager = mock()
    val contactsPersistenceManager: ContactsPersistenceManager = mock()
    val relayClientManager: RelayClientManager = mock()
    val messageSender: MessageSender = mock()
    val messageReceiver: MessageReceiver = mock()

    val contactEvents: PublishSubject<ContactEvent> = PublishSubject.create()

    val relayEvents: PublishSubject<RelayClientEvent> = PublishSubject.create()

    val messageSent: PublishSubject<MessageMetadata> = PublishSubject.create()

    fun createService(): MessengerServiceImpl {
        whenever(messageSender.messageSent).thenReturn(messageSent)

        whenever(contactsService.contactEvents).thenReturn(contactEvents)

        whenever(relayClientManager.events).thenReturn(relayEvents)

        whenever(messageSender.addToQueue(any())).thenReturn(Unit)
        whenever(messageSender.addToQueue(any(), any())).thenReturn(Unit)

        //some useful defaults
        whenever(messagePersistenceManager.addMessage(any(), any())).thenAnswer {
            val a = it.arguments[1] as MessageInfo
            Promise.ofSuccess<MessageInfo, Exception>(a)
        }

        whenever(contactsService.addMissingContacts(any())).thenReturn(emptySet())
        whenever(messageReceiver.processPackages(any())).thenReturn(Unit)

        whenever(groupPersistenceManager.part(any())).thenReturn(true)

        return MessengerServiceImpl(
            contactsService,
            messagePersistenceManager,
            groupPersistenceManager,
            contactsPersistenceManager,
            relayClientManager,
            messageSender,
            messageReceiver,
            selfId
        )
    }

    fun randomMessage(): String = randomUUID()

    fun randomTextSingleMetadata(): MessageMetadata {
        return MessageMetadata(
            randomUserId(),
            null,
            MessageCategory.TEXT_SINGLE,
            randomMessageId()
        )
    }

    fun wheneverAllowMessagesFrom(fn: (Set<UserId>) -> Promise<Set<UserId>, Exception>) {
        whenever(contactsService.allowMessagesFrom(anySet())).thenAnswer {
            @Suppress("UNCHECKED_CAST")
            val a = it.arguments[0] as Set<UserId>
            fn(a)
        }
    }

    fun wheneverExists(fn: (Set<UserId>) -> Promise<Set<UserId>, Exception>) {
        whenever(contactsPersistenceManager.exists(anySet())).thenAnswer {
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

        val ev = ReceivedMessage(sender, "payload", "messageId")

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
        val ev = ReceivedMessage(sender, newMessagePayload("payload"), messageId)

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
        val ev = ReceivedMessage(sender, newMessagePayload("payload"), messageId)

        setAllowAllUsers()

        wheneverExists { Promise.ofSuccess(it) }

        relayEvents.onNext(ev)

        verify(relayClientManager).sendMessageReceivedAck(messageId)
    }

    @Test
    fun `it should proxy new messages from MessageReceiver`() {
        val subject = PublishSubject.create<MessageBundle>()
        whenever(messageReceiver.newMessages).thenReturn(subject)

        val messengerService = createService()

        val testSubscriber = messengerService.newMessages.testSubscriber()

        val bundle = MessageBundle(UserId(1), listOf(
            MessageInfo.newReceived("m", currentTimestamp())
        ))

        subject.onNext(bundle)

        val bundles = testSubscriber.onNextEvents

        assertThat(bundles)
            .containsOnlyElementsOf(listOf(bundle))
            .`as`("Received bundles")
    }

    @Test
    fun `it should queue a single text message to be sent when sendMessageTo is called`() {
        val messengerService = createService()

        val recipient = randomUserId()

        messengerService.sendMessageTo(recipient, randomMessage())

        verify(messageSender).addToQueue(capture {
            assertEquals(recipient, it.userId, "Invalid recipient")
            assertEquals(MessageCategory.TEXT_SINGLE, it.category, "Invalid category")
        }, any())
    }

    //also doubles as checking for mark as delivered
    @Test
    fun `it should emit a message updated event when receiving a message update for TEXT_SINGLE message`() {
        val messengerService = createService()

        val update = randomTextSingleMetadata()
        val messageInfo = MessageInfo.newSent(update.messageId, 0).copy(isDelivered = true)

        whenever(messagePersistenceManager.markMessageAsDelivered(update.userId, update.messageId)).thenReturn(messageInfo)

        val testSubscriber = messengerService.messageUpdates.testSubscriber()

        messageSent.onNext(update)

        assertEventEmitted(testSubscriber) {
            assertEquals(update.userId, it.userId, "Invalid user id")
            assertNull(it.groupId, "groupId should be null")
            assertThat(it.messages)
                .containsOnly(messageInfo)
        }
    }

    @Test
    fun `it should emit a message updated event when receiving a message update for TEXT_GROUP message`() {
        val messengerService = createService()

        val update = randomTextGroupMetadata()
        val messageInfo = MessageInfo.newSent(update.messageId, 0).copy(isDelivered = true)

        whenever(groupPersistenceManager.markMessageAsDelivered(update.groupId!!, update.messageId))
            .thenReturn(GroupMessageInfo(update.userId, messageInfo))

        val testSubscriber = messengerService.messageUpdates.testSubscriber()

        messageSent.onNext(update)

        assertEventEmitted(testSubscriber) {
            assertEquals(update.userId, it.userId, "Invalid user id")
            assertEquals(update.groupId, it.groupId, "Invalid group id")
            assertThat(it.messages)
                .containsOnly(messageInfo)
        }
    }

    @Test
    fun `it should send the proper number of group messages when creating a group text message`() {
        val groupId = randomGroupId()
        val members = randomGroupMembers()

        val messengerService = createService()

        whenever(groupPersistenceManager.getMembers(groupId)).thenReturn(members)

        messengerService.sendGroupMessageTo(groupId, "msg")

        verify(messageSender).addToQueue(capture<List<SenderMessageEntry>> {
            assertEquals(members, it.map { it.metadata.userId }.toSet(), "Member list is invalid")
        })
    }

    @Test
    fun `it should add to the message log but send nothing for groups with no more members`() {
        val groupId = randomGroupId()

        val messengerService = createService()

        whenever(groupPersistenceManager.getMembers(groupId)).thenReturn(emptySet())

        val message = "msg"
        messengerService.sendGroupMessageTo(groupId, message)

        verify(groupPersistenceManager).addMessage(eq(groupId), capture {
            assertEquals(message, it.info.message, "Message is invalid")
        })

        verify(messageSender, never()).addToQueue(any())
    }

    @Test
    fun `it should store only one message to the log when creating a group text message`() {
        val groupId = randomGroupId()
        val members = randomGroupMembers()

        val messengerService = createService()

        whenever(groupPersistenceManager.getMembers(groupId)).thenReturn(members)

        val message = "msg"

        messengerService.sendGroupMessageTo(groupId, message)

        verify(groupPersistenceManager).addMessage(eq(groupId), capture {
            assertEquals(message, it.info.message, "Text message doesn't match")
        })
    }
    @Test
    fun `deleteMessages should proxy the call to MessagePersistenceManager`() {
        val messengerService = createService()

        val userId = randomUserId()
        val ids = (0..1).map { randomMessageId() }

        messengerService.deleteMessages(userId, ids)

        verify(messagePersistenceManager).deleteMessages(userId, ids)
    }

    @Test
    fun `deleteAllMessages should proxy the call to MessagePersistenceManager`() {
        val messengerService = createService()

        val userId = randomUserId()

        messengerService.deleteAllMessages(userId)

        verify(messagePersistenceManager).deleteAllMessages(userId)
    }

    @Test
    fun `deleteGroupMessages should proxy the call to GroupPersistenceManager`() {
        val messengerService = createService()

        val groupId = randomGroupId()
        val ids = (0..1).map { randomMessageId() }

        messengerService.deleteGroupMessages(groupId, ids)

        verify(groupPersistenceManager).deleteMessages(groupId, ids)
    }

    @Test
    fun `deleteAllGroupMessages should proxy the call to GroupPersistenceManager`() {
        val messengerService = createService()

        val groupId = randomGroupId()

        messengerService.deleteAllGroupMessages(groupId)

        verify(groupPersistenceManager).deleteAllMessages(groupId)
    }

    @Test
    fun `leaveGroup should leave the given group`() {
        val messengerService = createService()

        val groupId = randomGroupId()

        whenever(groupPersistenceManager.getMembers(groupId)).thenReturn(randomUserIds())

        messengerService.leaveGroup(groupId).get()

        verify(groupPersistenceManager).part(groupId)
    }

    inline fun <reified T : SlyMessage> convertFromSerialized(messageEntry: SenderMessageEntry): T {
        val objectMapper = ObjectMapper()
        return objectMapper.readValue(messageEntry.message, T::class.java)
    }

    //cheating a little here
    inline fun <reified T : SlyMessage> convertFromSerialized(messageEntries: List<SenderMessageEntry>): List<T> {
        val objectMapper = ObjectMapper()

        return messageEntries.map {
            objectMapper.readValue(it.message, T::class.java)
        }
    }

    @Test
    fun `leaveGroup should queue part messages to all members`() {
        val messengerService = createService()

        val groupId = randomGroupId()
        val members = randomUserIds()

        whenever(groupPersistenceManager.getMembers(groupId)).thenReturn(members)

        messengerService.leaveGroup(groupId).get()

        verify(messageSender).addToQueue(capture {
            assertEquals(members, it.mapToSet { it.metadata.userId }, "Invalid users")
            val messages = convertFromSerialized<GroupEventMessageWrapper>(it)
            messages.forEach {
                assertTrue(it.m is GroupEventMessage.Part, "Invalid message type")
            }
        })
    }

    @Test
    fun `leaveGroup should not queue any messages when no members remain in the group`() {
        val messengerService = createService()

        val groupId = randomGroupId()

        whenever(groupPersistenceManager.getMembers(groupId)).thenReturn(emptySet())

        messengerService.leaveGroup(groupId).get()

        verify(messageSender, never()).addToQueue(any())
    }

    inline fun <reified T> assertNoGroupMessagesSent() {
        val captor = argumentCaptor<List<SenderMessageEntry>>()
        verify(messageSender, atLeast(0)).addToQueue(capture(captor))

        val messages = captor.allValues.flatten()

        if (messages.isEmpty())
            return

        messages.forEach {
            val recipient = it.metadata.userId

            val wrapper = convertFromSerialized<GroupEventMessageWrapper>(it)

            if (wrapper.m is T)
                throw AssertionError("Unexpected ${T::class.simpleName} message to $recipient")
        }
    }

    /** Assert that the given group message type was sent to everyone in the given list, and that it satisifies certain conditions. */
    inline fun <reified T> assertGroupMessagesSentTo(expectedRecipients: Set<UserId>, asserter: (UserId, T) -> Unit) {
        val captor = argumentCaptor<List<SenderMessageEntry>>()
        verify(messageSender, atLeastOnce()).addToQueue(capture(captor))

        val messages = captor.allValues.flatten()

        assertTrue(messages.isNotEmpty(), "No messages were sent")

        val sendTo = HashMap<UserId, Boolean>()
        sendTo.putAll(expectedRecipients.map { it to false })

        messages.forEach {
            val recipient = it.metadata.userId

            val wrapper = convertFromSerialized<GroupEventMessageWrapper>(it)

            val m = wrapper.m as? T

            if (m != null) {
                asserter(recipient, m)

                assertTrue(recipient in sendTo, "Unexpected recipient")
                sendTo[recipient] = true
            }
        }

        val missing = HashSet(expectedRecipients)
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

        whenever(groupPersistenceManager.getInfo(groupId)).thenReturn(groupInfo)
        whenever(groupPersistenceManager.getMembers(groupId)).thenReturn(members)

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

            messengerService.inviteUsersToGroup(groupInfo.id, newMembers)

            assertGroupMessagesSentTo<GroupEventMessage.Join>(members) { recipient, m ->
                assertEquals(newMembers, m.joined, "Joined member list is incorrect")
            }
        }
    }

    @Test
    fun `inviteUsersToGroup should send an invitation message to each new member`() {
        withExistingGroup { messengerService, groupInfo, members ->
            val newMembers = randomUserIds()

            messengerService.inviteUsersToGroup(groupInfo.id, newMembers)

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

            messengerService.inviteUsersToGroup(groupInfo.id, newMembers)

            verify(groupPersistenceManager).addMembers(groupInfo.id, newMembers)
        }
    }

    @Test
    fun `createNewGroup should sent invitations to each initial member`() {
        val groupName = randomGroupName()
        val initialMembers = randomUserIds()

        val messengerService = createService()

        messengerService.createNewGroup(groupName, initialMembers)

        assertGroupMessagesSentTo<GroupEventMessage.Invitation>(initialMembers) { recipient, m ->
            val expectedMembers = HashSet(initialMembers)
            expectedMembers.remove(recipient)

            assertEquals(expectedMembers, m.members, "Invalid members list")
            assertEquals(groupName, m.name, "Invalid group name")
        }
    }

    @Test
    fun `createNewGroup should not sent invitations if given no initial members`() {
        val groupName = randomGroupName()

        val messengerService = createService()

        messengerService.createNewGroup(groupName, emptySet())

        assertNoGroupMessagesSent<GroupEventMessage.Invitation>()
    }

    @Test
    fun `createNewGroup should store the new group data`() {
        val groupName = randomGroupName()
        val initialMembers = randomUserIds()

        val messengerService = createService()

        messengerService.createNewGroup(groupName, initialMembers)

        verify(groupPersistenceManager).join(capture {
            assertEquals(groupName, it.name, "Invalid group name")
            assertEquals(GroupMembershipLevel.JOINED, it.membershipLevel, "Invalid membership level")
            assertFalse(it.isPending, "Created group should not be in pending state")
        }, eq(initialMembers))
    }
}