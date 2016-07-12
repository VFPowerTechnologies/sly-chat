package io.slychat.messenger.services

import com.fasterxml.jackson.databind.ObjectMapper
import com.nhaarman.mockito_kotlin.*
import io.slychat.messenger.core.SlyAddress
import io.slychat.messenger.core.UserId
import io.slychat.messenger.core.crypto.generateNewKeyVault
import io.slychat.messenger.core.currentTimestamp
import io.slychat.messenger.core.persistence.*
import io.slychat.messenger.core.randomUUID
import io.slychat.messenger.core.relay.DeviceMismatch
import io.slychat.messenger.core.relay.ReceivedMessage
import io.slychat.messenger.core.relay.RelayClientEvent
import io.slychat.messenger.core.relay.ServerReceivedMessage
import io.slychat.messenger.core.relay.base.DeviceMismatchContent
import io.slychat.messenger.services.crypto.DeviceUpdateResult
import io.slychat.messenger.services.crypto.EncryptedPackagePayloadV0
import io.slychat.messenger.services.crypto.MessageCipherService
import io.slychat.messenger.services.crypto.MessageData
import io.slychat.messenger.testutils.KovenantTestModeRule
import io.slychat.messenger.testutils.thenReturn
import nl.komponents.kovenant.Promise
import org.assertj.core.api.Assertions.assertThat
import org.junit.ClassRule
import org.junit.Test
import rx.Scheduler
import rx.schedulers.Schedulers
import rx.subjects.PublishSubject
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@Suppress("UNUSED_VARIABLE")
class MessengerServiceImplTest {
    companion object {
        @JvmField
        @ClassRule
        val kovenantTestMode = KovenantTestModeRule()

        val testPassword = "test"
        val testKeyVault = generateNewKeyVault(testPassword)

        val testUserAddress = SlyAddress(UserId(1), 1)
        val userLoginData = UserData(testUserAddress, testKeyVault)
    }

    val scheduler: Scheduler = Schedulers.immediate()
    val contactsService: ContactsService = mock()
    val messagePersistenceManager: MessagePersistenceManager = mock()
    val contactsPersistenceManager: ContactsPersistenceManager = mock()
    val relayClientManager: RelayClientManager = mock()
    val messageCipherService: MessageCipherService = mock()
    val messageReceiver: MessageReceiver = mock()

    val encryptedMessages: PublishSubject<EncryptionResult> = PublishSubject.create()
    val deviceUpdates: PublishSubject<DeviceUpdateResult>?= PublishSubject.create()

    val contactEvents: PublishSubject<ContactEvent> = PublishSubject.create()

    val relayEvents: PublishSubject<RelayClientEvent> = PublishSubject.create()
    val relayOnlineStatus: PublishSubject<Boolean> = PublishSubject.create()

    fun createService(relayOnlineStatus: Boolean = false): MessengerServiceImpl {
        whenever(messageCipherService.encryptedMessages).thenReturn(encryptedMessages)
        whenever(messageCipherService.deviceUpdates).thenReturn(deviceUpdates)

        whenever(contactsService.contactEvents).thenReturn(contactEvents)

        whenever(relayClientManager.events).thenReturn(relayEvents)
        whenever(relayClientManager.onlineStatus).thenReturn(this.relayOnlineStatus)

        whenever(relayClientManager.isOnline).thenReturn(relayOnlineStatus)

        //some useful defaults
        whenever(messagePersistenceManager.getUndeliveredMessages()).thenReturn(emptyMap())
        whenever(contactsService.addMissingContacts(any())).thenReturn(emptySet())
        whenever(messageReceiver.processPackages(any())).thenReturn(Unit)

        return MessengerServiceImpl(
            scheduler,
            contactsService,
            messagePersistenceManager,
            contactsPersistenceManager,
            relayClientManager,
            messageCipherService,
            messageReceiver,
            userLoginData
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

        whenever(messagePersistenceManager.addToQueue(anyCollection())).thenReturn(Unit)

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
    fun `it should persist a message when sendMessageTo is called for a different user`() {
        val messengerService = createService()

        val to = UserId(2)

        handleAddMessage(to)

        messengerService.sendMessageTo(to, "message")

        verify(messagePersistenceManager).addMessage(eq(to), capture {
            assertTrue(it.isSent, "Not mark as sent")
        })
    }

    @Test
    fun `it should persist a message when sendMessageTo is called for self`() {
        val messengerService = createService()

        val to = userLoginData.userId

        handleAddMessage(to)

        messengerService.sendMessageTo(to, "message")

        val captor = argumentCaptor<MessageInfo>()
        verify(messagePersistenceManager, times(2)).addMessage(eq(to), capture(captor))

        val values = captor.allValues

        val sentMessage = values[0]
        assertTrue(sentMessage.isSent, "Message not marked as sent")
        assertTrue(sentMessage.isDelivered, "Message not marked as delivered")

        val receivedMessage = values[1]
        assertFalse(receivedMessage.isSent, "Message not marked as received")
    }

    @Test
    fun `it should call MessageCipherService to do a device refresh when receiving a DeviceMismatch from the relay`() {
        val messengerService = createService()

        val content = DeviceMismatchContent(listOf(1), listOf(2), listOf(3))
        val to = UserId(2)
        val ev = DeviceMismatch(to, randomUUID(), content)

        setRelayOnlineStatus(true)

        handleAddMessage(to)

        messengerService.sendMessageTo(to, "message")

        relayEvents.onNext(ev)

        verify(messageCipherService).updateDevices(to, content)
    }

    @Test
    fun `it should retrieve all undelivered messages when the relay comes online`() {
        val messengerService = createService()

        relayOnlineStatus.onNext(true)

        verify(messagePersistenceManager).getUndeliveredMessages()
    }

    @Test
    fun `it should mark a sent message as delievered when receiving a confirmation from the relay`() {
        val messengerService = createService(true)

        val to = UserId(2)

        handleAddMessage(to)

        val connectionTag = 3

        whenever(relayClientManager.connectionTag).thenReturn(connectionTag)

        val message = "message"
        messengerService.sendMessageTo(to, message)

        verify(messageCipherService).encrypt(eq(to), any(), any())
        val data = MessageData(1, 1, EncryptedPackagePayloadV0(false, ByteArray(0)))
        encryptedMessages.onNext(EncryptionOk(listOf(data), connectionTag))

        val captor = argumentCaptor<String>()

        verify(relayClientManager).sendMessage(any(), eq(to), any(), capture(captor))

        val messageId = captor.value

        val ev = ServerReceivedMessage(to, messageId)

        val messageInfo = MessageInfo.newSent(messageId, message, currentTimestamp(), currentTimestamp(), 0)
        whenever(messagePersistenceManager.markMessageAsDelivered(to, messageId)).thenReturn(messageInfo)

        relayEvents.onNext(ev)

        verify(messagePersistenceManager).markMessageAsDelivered(to, messageId)

        //TODO this also fires a message update event which we should check for
    }

    //TODO message send/receive tests

    //TODO rejecting decryption results for invalid message ids

    @Test
    fun `it should drop group messages when not a member`() {

    }

    @Test
    fun `it should drop group messages when sender is not a member`() {}
}