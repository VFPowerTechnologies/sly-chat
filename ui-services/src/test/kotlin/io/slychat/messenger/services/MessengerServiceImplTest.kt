package io.slychat.messenger.services

import com.fasterxml.jackson.databind.ObjectMapper
import com.nhaarman.mockito_kotlin.*
import io.slychat.messenger.core.SlyAddress
import io.slychat.messenger.core.UserId
import io.slychat.messenger.core.currentTimestamp
import io.slychat.messenger.core.persistence.*
import io.slychat.messenger.core.randomUUID
import io.slychat.messenger.core.relay.ReceivedMessage
import io.slychat.messenger.core.relay.RelayClientEvent
import io.slychat.messenger.services.crypto.EncryptedPackagePayloadV0
import io.slychat.messenger.testutils.KovenantTestModeRule
import io.slychat.messenger.testutils.testSubscriber
import io.slychat.messenger.testutils.thenReturn
import nl.komponents.kovenant.Promise
import org.assertj.core.api.Assertions.assertThat
import org.junit.ClassRule
import org.junit.Test
import rx.subjects.PublishSubject
import kotlin.test.assertEquals
import kotlin.test.assertNull

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

        whenever(messageSender.addToQueue(any(), any())).thenReturn(Unit)

        //some useful defaults
        whenever(messagePersistenceManager.getUndeliveredMessages()).thenReturn(emptyMap())
        whenever(contactsService.addMissingContacts(any())).thenReturn(emptySet())
        whenever(messageReceiver.processPackages(any())).thenReturn(Unit)

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

    fun randomTextGroupMetaData(): MessageMetadata {
        return MessageMetadata(
            randomUserId(),
            randomGroupId(),
            MessageCategory.TEXT_GROUP,
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
        //FIXME
        return objectMapper.writeValueAsString(EncryptedPackagePayloadV0(false, message.toByteArray()))
    }

    fun setRelayOnlineStatus(isOnline: Boolean) {
        whenever(relayClientManager.isOnline).thenReturn(isOnline)
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

    fun handleAddMessage(to: UserId) {
        whenever(messagePersistenceManager.addMessage(eq(to), any())).thenAnswer {
            val a = it.arguments[1] as MessageInfo
            Promise.ofSuccess<MessageInfo, Exception>(a)
        }
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
        val messageService = createService()

        val update = randomTextSingleMetadata()
        val messageInfo = MessageInfo.newSent(update.messageId, 0).copy(isDelivered = true)

        whenever(messagePersistenceManager.markMessageAsDelivered(update.userId, update.messageId)).thenReturn(messageInfo)

        val testSubscriber = messageService.messageUpdates.testSubscriber()

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
        val messageService = createService()

        val update = randomTextGroupMetaData()
        val messageInfo = MessageInfo.newSent(update.messageId, 0).copy(isDelivered = true)

        whenever(groupPersistenceManager.markMessageAsDelivered(update.groupId!!, update.messageId)).thenReturn(messageInfo)

        val testSubscriber = messageService.messageUpdates.testSubscriber()

        messageSent.onNext(update)

        assertEventEmitted(testSubscriber) {
            assertEquals(update.userId, it.userId, "Invalid user id")
            assertEquals(update.groupId, it.groupId, "Invalid group id")
            assertThat(it.messages)
                .containsOnly(messageInfo)
        }
    }
}