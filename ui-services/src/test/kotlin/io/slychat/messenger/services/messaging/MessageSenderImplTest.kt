package io.slychat.messenger.services.messaging

import com.nhaarman.mockito_kotlin.*
import io.slychat.messenger.core.currentTimestamp
import io.slychat.messenger.core.mapToSet
import io.slychat.messenger.core.persistence.MessageCategory
import io.slychat.messenger.core.persistence.MessageMetadata
import io.slychat.messenger.core.persistence.MessageQueuePersistenceManager
import io.slychat.messenger.core.persistence.QueuedMessage
import io.slychat.messenger.core.randomUUID
import io.slychat.messenger.core.relay.*
import io.slychat.messenger.core.relay.base.DeviceMismatchContent
import io.slychat.messenger.services.*
import io.slychat.messenger.services.crypto.DeviceUpdateResult
import io.slychat.messenger.services.crypto.EncryptedPackagePayloadV0
import io.slychat.messenger.services.crypto.MessageCipherService
import io.slychat.messenger.services.crypto.MessageData
import io.slychat.messenger.testutils.KovenantTestModeRule
import io.slychat.messenger.testutils.TestException
import io.slychat.messenger.testutils.testSubscriber
import io.slychat.messenger.testutils.thenReturn
import org.junit.ClassRule
import org.junit.Test
import rx.schedulers.Schedulers
import rx.subjects.BehaviorSubject
import rx.subjects.PublishSubject
import java.util.*
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class MessageSenderImplTest {
    companion object {
        @JvmField
        @ClassRule
        val kovenantTestMode = KovenantTestModeRule()
    }

    val messageCipherService: MessageCipherService = mock()
    val relayClientManager: RelayClientManager = mock()
    val messageQueuePersistenceManager: MessageQueuePersistenceManager = mock()

    val relayEvents: PublishSubject<RelayClientEvent> = PublishSubject.create()
    val relayOnlineStatus: BehaviorSubject<Boolean> = BehaviorSubject.create()

    val encryptionResults: PublishSubject<EncryptionResult> = PublishSubject.create()
    val deviceUpdates: PublishSubject<DeviceUpdateResult> = PublishSubject.create()

    val defaultConnectionTag = Random().nextInt(Int.MAX_VALUE)

    fun setRelayOnlineStatus(isOnline: Boolean) {
        relayOnlineStatus.onNext(isOnline)
        whenever(relayClientManager.isOnline).thenReturn(isOnline)
    }

    fun createSender(
        relayIsOnline: Boolean = false,
        initialQueuedMessages: List<QueuedMessage> = emptyList()
    ): MessageSenderImpl {
        setRelayOnlineStatus(relayIsOnline)

        whenever(messageQueuePersistenceManager.add(any<List<QueuedMessage>>())).thenReturn(Unit)
        whenever(messageQueuePersistenceManager.add(any<QueuedMessage>())).thenReturn(Unit)
        whenever(messageQueuePersistenceManager.remove(any(), any())).thenReturn(Unit)
        whenever(messageQueuePersistenceManager.getUndelivered()).thenReturn(initialQueuedMessages)

        whenever(relayClientManager.events).thenReturn(relayEvents)
        whenever(relayClientManager.onlineStatus).thenReturn(relayOnlineStatus)
        whenever(relayClientManager.connectionTag).thenReturn(defaultConnectionTag)

        whenever(messageCipherService.encryptedMessages).thenReturn(encryptionResults)
        whenever(messageCipherService.deviceUpdates).thenReturn(deviceUpdates)

        return MessageSenderImpl(
            Schedulers.immediate(),
            messageCipherService,
            relayClientManager,
            messageQueuePersistenceManager
        )
    }

    fun randomQueuedMessage(): QueuedMessage {
        val recipient = randomUserId()
        val messageId = randomUUID()
        val serialized = randomMessage()

        val metadata = MessageMetadata(
            recipient,
            null,
            MessageCategory.TEXT_SINGLE,
            messageId
        )

        val queued = QueuedMessage(
            metadata,
            currentTimestamp(),
            serialized
        )

        return queued
    }

    fun randomMessage(): ByteArray = Random().nextInt(100).toString().toByteArray()

    fun randomEncryptedPayload(): EncryptedPackagePayloadV0 =
        EncryptedPackagePayloadV0(true, ByteArray(0))

    @Test
    fun `it should read all queued messages when a relay connection is available on startup`() {
        val sender = createSender(true)

        verify(messageQueuePersistenceManager).getUndelivered()
    }

    @Test
    fun `it should reread all queued messages when a relay connection is established`() {
        val sender = createSender(false)

        setRelayOnlineStatus(true)

        verify(messageQueuePersistenceManager).getUndelivered()
    }

    @Test
    fun `it should queue messages while the relay is offline`() {
        val sender = createSender()

        val queued = randomQueuedMessage()

        sender.addToQueue(queued.metadata, queued.serialized).get()

        verify(messageQueuePersistenceManager).add(capture<QueuedMessage> {
            assertEquals(queued.metadata.messageId, it.metadata.messageId, "Invalid message id")
            assertEquals(queued.metadata.userId, it.metadata.userId, "Invalid recipient")
        })

        verify(messageCipherService, never()).encrypt(any(), any(), any())
    }

    @Test
    fun `it should process queued messages when a relay connection is available on startup`() {
        val queued = randomQueuedMessage()

        val sender = createSender(true, listOf(queued))

        verify(messageCipherService).encrypt(queued.metadata.userId, queued.serialized, defaultConnectionTag)
    }

    @Test
    fun `it should process messages when a relay connection is available and no messages are currently queued`() {
        val queued = randomQueuedMessage()

        val sender = createSender(true)

        sender.addToQueue(queued.metadata, queued.serialized).get()

        verify(messageCipherService).encrypt(queued.metadata.userId, queued.serialized, defaultConnectionTag)
    }

    @Test
    fun `it should queue messages when the relay is online and a message is already being processed`() {
        val first = randomQueuedMessage()
        val sender = createSender(true, listOf(first))

        val second = randomQueuedMessage()
        sender.addToQueue(second.metadata, second.serialized).get()

        verify(messageQueuePersistenceManager).add(capture<QueuedMessage> {
            assertEquals(second.metadata.messageId, it.metadata.messageId, "Invalid message id")
        })

        verify(messageCipherService, times(1)).encrypt(first.metadata.userId, first.serialized, defaultConnectionTag)
    }

    //XXX these tests kinda bypass the encryption result part, so kinda nasty since it relies on a specific impl detail
    @Test
    fun `it should remove the package from the sent queue once a message has been acknowledged as delivered`() {
        val sender = createSender(true)

        val queued = randomQueuedMessage()

        val metadata = queued.metadata

        sender.addToQueue(metadata, queued.serialized).get()

        relayEvents.onNext(ServerReceivedMessage(metadata.userId, metadata.messageId))

        verify(messageQueuePersistenceManager).remove(metadata.userId, metadata.messageId)
    }

    @Test
    fun `it should process the next queued message once a successful send result has been received`() {
        val sender = createSender(true)

        val first = randomQueuedMessage()
        val second = randomQueuedMessage()

        sender.addToQueue(first.metadata, first.serialized).get()
        sender.addToQueue(second.metadata, second.serialized).get()

        relayEvents.onNext(ServerReceivedMessage(first.metadata.userId, first.metadata.messageId))

        val order = inOrder(messageCipherService)

        order.verify(messageCipherService).encrypt(eq(first.metadata.userId), any(), eq(defaultConnectionTag))
        order.verify(messageCipherService).encrypt(eq(second.metadata.userId), any(), eq(defaultConnectionTag))
    }

    @Test
    fun `it should send the encrypted message to the relay upon receiving a successful encryption result`() {
        val sender = createSender(true)

        val queued = randomQueuedMessage()
        val metadata = queued.metadata
        val recipient = metadata.userId

        sender.addToQueue(metadata, queued.serialized).get()

        val messageData = MessageData(1, 1, randomEncryptedPayload())
        val result = EncryptionOk(
            listOf(messageData),
            defaultConnectionTag
        )

        encryptionResults.onNext(result)

        val relayUserMessage = RelayUserMessage(messageData.deviceId, messageData.registrationId, messageData.payload)
        val relayMessageBundle = RelayMessageBundle(listOf(relayUserMessage))

        verify(relayClientManager).sendMessage(defaultConnectionTag, recipient, relayMessageBundle, metadata.messageId)
    }

    @Test
    fun `it should emit a message update event once a message has been acknowledged as delivered`() {
        val sender = createSender(true)

        val queued = randomQueuedMessage()
        val metadata = queued.metadata
        val recipient = metadata.userId

        val testSubscriber = sender.messageSent.testSubscriber()

        sender.addToQueue(metadata, queued.serialized).get()

        relayEvents.onNext(ServerReceivedMessage(recipient, metadata.messageId))

        assertEventEmitted(testSubscriber) {
            assertEquals(metadata, it, "Invalid message metadata")
        }
    }

    @Test
    fun `it should clear the message queue on a relay disconnect`() {
        val queued = randomQueuedMessage()

        val sender = createSender(true, listOf(queued))

        reset(messageCipherService)
        whenever(messageQueuePersistenceManager.getUndelivered()).thenReturn(emptyList())

        setRelayOnlineStatus(false)
        setRelayOnlineStatus(true)

        verify(messageCipherService, never()).encrypt(any(), any(), any())
    }

    @Test
    fun `it should discard encrypted messages when the relay is now offline`() {
        val queued = randomQueuedMessage()

        val sender = createSender(true, listOf(queued))

        setRelayOnlineStatus(false)

        encryptionResults.onNext(EncryptionOk(listOf(MessageData(1, 1, randomEncryptedPayload())), defaultConnectionTag))

        verify(relayClientManager, never()).sendMessage(any(), any(), any(), any())
    }

    @Test
    fun `it should call MessageCipherService to do a device refresh when receiving a DeviceMismatch from the relay`() {
        val sender = createSender(true)

        val queued = randomQueuedMessage()

        val content = DeviceMismatchContent(listOf(1), listOf(2), listOf(3))
        val to = queued.metadata.userId
        val ev = DeviceMismatch(to, queued.metadata.messageId, content)

        sender.addToQueue(queued.metadata, queued.serialized).get()

        relayEvents.onNext(ev)

        verify(messageCipherService).updateDevices(to, content)
    }

    @Test
    fun `it should resubmit the current message for encryption after a successful device mismatch update`() {
        val sender = createSender(true)

        val queued = randomQueuedMessage()

        sender.addToQueue(queued.metadata, queued.serialized).get()

        reset(messageCipherService)

        val ev = DeviceUpdateResult(null)

        deviceUpdates.onNext(ev)

        verify(messageCipherService).encrypt(queued.metadata.userId, queued.serialized, defaultConnectionTag)
    }

    @Test
    fun `addQueue(message) should propagate failures if writing to the send queue fails`() {
        val sender = createSender(true)

        val queued = randomQueuedMessage()

        whenever(messageQueuePersistenceManager.add(any<QueuedMessage>())).thenReturn(TestException())

        assertFailsWith(TestException::class) {
            sender.addToQueue(queued.metadata, queued.serialized).get()
        }
    }

    @Test
    fun `addToQueue(messages) should add all given messages to the queue`() {
        val sender = createSender(false)

        val groupId = randomGroupId()

        val messages = (0..1).map {
            SenderMessageEntry(
                randomTextGroupMetaData(groupId),
                randomMessage()
            )
        }

        val members = messages.mapToSet { it.metadata.userId }

        sender.addToQueue(messages).get()

        verify(messageQueuePersistenceManager).add(capture<Iterable<QueuedMessage>> {
            val sentTo = it.mapToSet { it.metadata.userId }
            assertEquals(members, sentTo, "Invalid users")
        })
    }
}