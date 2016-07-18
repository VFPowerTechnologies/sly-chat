package io.slychat.messenger.services

import com.nhaarman.mockito_kotlin.*
import io.slychat.messenger.core.UserId
import io.slychat.messenger.core.currentTimestamp
import io.slychat.messenger.core.persistence.MessagePersistenceManager
import io.slychat.messenger.core.persistence.MessageQueuePersistenceManager
import io.slychat.messenger.core.persistence.QueuedMessage
import io.slychat.messenger.core.randomUUID
import io.slychat.messenger.core.relay.RelayClientEvent
import io.slychat.messenger.core.relay.RelayMessageBundle
import io.slychat.messenger.core.relay.RelayUserMessage
import io.slychat.messenger.core.relay.ServerReceivedMessage
import io.slychat.messenger.services.crypto.DeviceUpdateResult
import io.slychat.messenger.services.crypto.EncryptedPackagePayloadV0
import io.slychat.messenger.services.crypto.MessageCipherService
import io.slychat.messenger.services.crypto.MessageData
import io.slychat.messenger.testutils.KovenantTestModeRule
import io.slychat.messenger.testutils.testSubscriber
import io.slychat.messenger.testutils.thenReturn
import org.assertj.core.api.Assertions.assertThat
import org.junit.ClassRule
import org.junit.Ignore
import org.junit.Test
import rx.schedulers.Schedulers
import rx.subjects.BehaviorSubject
import rx.subjects.PublishSubject
import java.util.*
import kotlin.test.assertEquals

class MessageSenderImplTest {
    companion object {
        @JvmField
        @ClassRule
        val kovenantTestMode = KovenantTestModeRule()
    }

    val messageCipherService: MessageCipherService = mock()
    val relayClientManager: RelayClientManager = mock()
    val messagePersistenceManager: MessagePersistenceManager = mock()
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

        whenever(messageQueuePersistenceManager.add(any())).thenReturn(Unit)
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
            messagePersistenceManager,
            messageQueuePersistenceManager
        )
    }

    fun randomUserId(): UserId {
        val l = 1 + Random().nextInt(1000-1) + 1
        return UserId(l.toLong())
    }

    fun randomQueuedMessage(): QueuedMessage {
        val recipient = randomUserId()
        val messageId = randomUUID()
        val serialized = randomMessage()

        val queued = QueuedMessage(
            recipient,
            messageId,
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

        val recipient = UserId(1)

        val messageId = randomUUID()

        sender.addToQueue(recipient, messageId, randomMessage()).get()

        verify(messageQueuePersistenceManager).add(capture {
            assertEquals(messageId, it.messageId, "Invalid message id")
            assertEquals(recipient, it.userId, "Invalid recipient")
        })

        verify(messageCipherService, never()).encrypt(any(), any(), any())
    }

    @Test
    fun `it should process queued messages when a relay connection is available on startup`() {
        val queued = randomQueuedMessage()

        val sender = createSender(true, listOf(queued))

        verify(messageCipherService).encrypt(queued.userId, queued.serialized, defaultConnectionTag)
    }

    @Test
    fun `it should process messages when a relay connection is available and no messages are currently queued`() {
        val queued = randomQueuedMessage()

        val sender = createSender(true)

        sender.addToQueue(queued.userId, queued.messageId, queued.serialized).get()

        verify(messageCipherService).encrypt(queued.userId, queued.serialized, defaultConnectionTag)
    }

    @Test
    fun `it should queue messages when the relay is online and a message is already being processed`() {
        val queued = randomQueuedMessage()
        val sender = createSender(true, listOf(queued))

        val second = randomQueuedMessage()
        sender.addToQueue(second.userId, second.messageId, second.serialized).get()

        verify(messageQueuePersistenceManager).add(capture {
            assertEquals(second.messageId, it.messageId, "Invalid message id")
        })

        verify(messageCipherService, times(1)).encrypt(queued.userId, queued.serialized, defaultConnectionTag)
    }

    //XXX these tests kinda bypass the encryption result part, so kinda nasty since it relies on a specific impl detail
    @Test
    fun `it should remove the package from the sent queue once a message has been acknowledged as delivered`() {
        val sender = createSender(true)

        val recipient = UserId(1)

        val messageId = randomUUID()

        sender.addToQueue(recipient, messageId, randomMessage()).get()

        relayEvents.onNext(ServerReceivedMessage(recipient, messageId))

        verify(messageQueuePersistenceManager).remove(recipient, messageId)
    }

    @Test
    fun `it should process the next queued message once a successful send result has been received`() {
        val sender = createSender(true)

        val one = randomQueuedMessage()
        val two = randomQueuedMessage()

        sender.addToQueue(one.userId, one.messageId, one.serialized).get()
        sender.addToQueue(two.userId, two.messageId, two.serialized).get()

        relayEvents.onNext(ServerReceivedMessage(one.userId, one.messageId))

        val order = inOrder(messageCipherService)

        order.verify(messageCipherService).encrypt(eq(one.userId), any(), eq(defaultConnectionTag))
        order.verify(messageCipherService).encrypt(eq(two.userId), any(), eq(defaultConnectionTag))
    }

    @Test
    fun `it should send the encrypted message to the relay upon receiving a successful encryption result`() {
        val sender = createSender(true)

        val recipient = randomUserId()

        val messageId = randomUUID()

        sender.addToQueue(recipient, messageId, randomMessage()).get()

        val messageData = MessageData(1, 1, randomEncryptedPayload())
        val result = EncryptionOk(
            listOf(messageData),
            defaultConnectionTag
        )

        encryptionResults.onNext(result)

        val relayUserMessage = RelayUserMessage(messageData.deviceId, messageData.registrationId, messageData.payload)
        val relayMessageBundle = RelayMessageBundle(listOf(relayUserMessage))

        verify(relayClientManager).sendMessage(defaultConnectionTag, recipient, relayMessageBundle, messageId)
    }

    @Ignore
    @Test
    fun `it should emit a message update event once a message has been acknowledged as delivered`() {
        val sender = createSender(true)

        val recipient = UserId(1)

        val testSubscriber = sender.messageUpdates.testSubscriber()

        val messageId = randomUUID()

        sender.addToQueue(recipient, messageId, randomMessage()).get()

        relayEvents.onNext(ServerReceivedMessage(recipient, messageId))

        val events = testSubscriber.onNextEvents
        assertThat(events)
            .hasSize(1)
            .`as`("Message update events")

        val ev = events[0]

        assertEquals(recipient, ev.userId, "MessageBundle for invalid user")
    }

    @Ignore
    @Test
    fun `it should start processing the next message once the current one has been sent`() {
        val sender = createSender(true)
        //TODO
    }

    @Ignore
    @Test
    fun `it should clear the message queue on a relay disconnect`() {}

    //TODO stuff for device mismatch, etc

    //TODO stuff for connection tag checking (eg discarding encrypted messages with diff tags and moving to the next message)
}