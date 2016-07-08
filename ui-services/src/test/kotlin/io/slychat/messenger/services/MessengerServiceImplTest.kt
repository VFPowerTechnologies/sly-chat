package io.slychat.messenger.services

import com.fasterxml.jackson.databind.ObjectMapper
import com.nhaarman.mockito_kotlin.*
import io.slychat.messenger.core.SlyAddress
import io.slychat.messenger.core.UserId
import io.slychat.messenger.core.crypto.generateNewKeyVault
import io.slychat.messenger.core.currentTimestamp
import io.slychat.messenger.core.persistence.ContactsPersistenceManager
import io.slychat.messenger.core.persistence.MessagePersistenceManager
import io.slychat.messenger.core.persistence.Package
import io.slychat.messenger.core.persistence.PackageId
import io.slychat.messenger.core.randomUUID
import io.slychat.messenger.core.relay.ReceivedMessage
import io.slychat.messenger.core.relay.RelayClientEvent
import io.slychat.messenger.services.crypto.DeviceUpdateResult
import io.slychat.messenger.services.crypto.EncryptedPackagePayloadV0
import io.slychat.messenger.services.crypto.MessageCipherService
import io.slychat.messenger.testutils.KovenantTestModeRule
import io.slychat.messenger.testutils.thenReturn
import nl.komponents.kovenant.Promise
import org.assertj.core.api.Assertions.assertThat
import org.junit.ClassRule
import org.junit.Test
import rx.Scheduler
import rx.schedulers.Schedulers
import rx.subjects.PublishSubject

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

    val encryptedMessages: PublishSubject<EncryptionResult> = PublishSubject.create()
    val decryptedMessages: PublishSubject<DecryptionResult> = PublishSubject.create()
    val deviceUpdates: PublishSubject<DeviceUpdateResult>?= PublishSubject.create()

    val contactEvents: PublishSubject<ContactEvent> = PublishSubject.create()

    val relayEvents: PublishSubject<RelayClientEvent> = PublishSubject.create()
    val relayOnlineStatus: PublishSubject<Boolean> = PublishSubject.create()

    fun createService(): MessengerServiceImpl {
        whenever(messageCipherService.decryptedMessages).thenReturn(decryptedMessages)
        whenever(messageCipherService.encryptedMessages).thenReturn(encryptedMessages)
        whenever(messageCipherService.deviceUpdates).thenReturn(deviceUpdates)

        whenever(contactsService.contactEvents).thenReturn(contactEvents)

        whenever(relayClientManager.events).thenReturn(relayEvents)
        whenever(relayClientManager.onlineStatus).thenReturn(relayOnlineStatus)

        return MessengerServiceImpl(
            scheduler,
            contactsService,
            messagePersistenceManager,
            contactsPersistenceManager,
            relayClientManager,
            messageCipherService,
            userLoginData
        )
    }

    @Test
    fun `it should add all received relay messages to the package queue`() {}

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
        val argumentCaptor = argumentCaptor<Collection<Package>>()
        verify(messagePersistenceManager).addToQueue(capture(argumentCaptor))
        return argumentCaptor.value
    }

    @Test
    fun `it should add all offline messages to the package queue`() {
        val messengerService = createService()

        val sender = SlyAddress(UserId(1), 1)

        val packages = (0..1).map {
            Package(PackageId(sender, randomUUID()), currentTimestamp(), "payload")
        }

        setAllowAllUsers()

        messengerService.addOfflineMessages(packages)

        val queued = getQueuedPackages()
        assertThat(queued)
            .containsOnlyElementsOf(packages)
            .`as`("Package list")
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

        verify(messagePersistenceManager).addToQueue(argThat<Collection<Package>>({ this.isEmpty() }))
    }

    fun newMessagePayload(message: String): String {
        val objectMapper = ObjectMapper()
        //FIXME
        return objectMapper.writeValueAsString(EncryptedPackagePayloadV0(false, message.toByteArray()))
    }

    @Test
    fun `it should notify the relay when a package has been queued`() {
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

    @Test
    fun `it should notify ContactsService when missing users are found`() {
        val messengerService = createService()

        val sender = SlyAddress(UserId(2), 1)
        val messageId = randomUUID()
        val ev = ReceivedMessage(sender, newMessagePayload("payload"), messageId)

        setAllowAllUsers()

        whenever(messagePersistenceManager.addToQueue(anyCollection())).thenReturn(Unit)

        wheneverExists { Promise.ofSuccess(emptySet()) }

        relayEvents.onNext(ev)

        verify(contactsService).doProcessUnaddedContacts()
    }
}