package io.slychat.messenger.services.messaging

import com.nhaarman.mockito_kotlin.*
import io.slychat.messenger.core.*
import io.slychat.messenger.core.crypto.randomMessageId
import io.slychat.messenger.core.persistence.MessageMetadata
import io.slychat.messenger.core.persistence.MessageQueuePersistenceManager
import io.slychat.messenger.core.persistence.SenderMessageEntry
import io.slychat.messenger.core.relay.*
import io.slychat.messenger.core.relay.base.DeviceMismatchContent
import io.slychat.messenger.services.MessageUpdateEvent
import io.slychat.messenger.services.RelayClientManager
import io.slychat.messenger.services.assertEventEmitted
import io.slychat.messenger.services.crypto.EncryptedPackagePayloadV0
import io.slychat.messenger.services.crypto.MessageCipherService
import io.slychat.messenger.services.crypto.MessageData
import io.slychat.messenger.testutils.*
import nl.komponents.kovenant.Promise
import nl.komponents.kovenant.deferred
import org.junit.Before
import org.junit.ClassRule
import org.junit.Ignore
import org.junit.Test
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

    private fun MessageSender.addToQueue(metadata: MessageMetadata, message: ByteArray): Promise<Unit, Exception> {
        return addToQueue(SenderMessageEntry(metadata, message))
    }

    val messageCipherService: MessageCipherService = mock()
    val relayClientManager: RelayClientManager = mock()
    val messageQueuePersistenceManager: MessageQueuePersistenceManager = mock()

    val relayEvents: PublishSubject<RelayClientEvent> = PublishSubject.create()
    val relayOnlineStatus: BehaviorSubject<Boolean> = BehaviorSubject.create()

    val messageUpdateEvents: PublishSubject<MessageUpdateEvent> = PublishSubject.create()

    val defaultConnectionTag = Random().nextInt(Int.MAX_VALUE)

    fun setRelayOnlineStatus(isOnline: Boolean) {
        relayOnlineStatus.onNext(isOnline)
        whenever(relayClientManager.isOnline).thenReturn(isOnline)
    }

    fun randomEncryptionResult(): EncryptionResult {
        val dummyMessageData = MessageData(randomDeviceId(), 0, randomEncryptedPayload())
        return EncryptionResult(listOf(dummyMessageData), defaultConnectionTag)
    }

    @Before
    fun before() {
        whenever(messageQueuePersistenceManager.add(any<SenderMessageEntry>())).thenResolve(Unit)

        whenever(messageQueuePersistenceManager.add(any<Collection<SenderMessageEntry>>())).thenResolve(Unit)

        whenever(messageQueuePersistenceManager.remove(any(), any())).thenResolve(true)
        whenever(messageQueuePersistenceManager.removeAll(any(), any())).thenResolve(true)
        whenever(messageQueuePersistenceManager.removeAllForConversation(any())).thenResolve(true)

        whenever(relayClientManager.events).thenReturn(relayEvents)
        whenever(relayClientManager.onlineStatus).thenReturn(relayOnlineStatus)
        whenever(relayClientManager.connectionTag).thenReturn(defaultConnectionTag)

        whenever(messageCipherService.encrypt(any(), any(), any())).thenResolve(randomEncryptionResult())
    }

    fun createSender(
        relayIsOnline: Boolean = false,
        initialEntries: List<SenderMessageEntry> = emptyList()
    ): MessageSenderImpl {
        setRelayOnlineStatus(relayIsOnline)

        whenever(messageQueuePersistenceManager.getUndelivered()).thenResolve(initialEntries)

        return MessageSenderImpl(
            messageCipherService,
            relayClientManager,
            messageQueuePersistenceManager,
            messageUpdateEvents
        )
    }

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

        val entry = randomSenderMessageEntry()

        sender.addToQueue(entry).get()

        verify(messageQueuePersistenceManager).add(entry)

        verify(messageCipherService, never()).encrypt(any(), any(), any())
    }

    @Test
    fun `it should process queued messages when a relay connection is available on startup`() {
        val entry = randomSenderMessageEntry()

        val sender = createSender(true, listOf(entry))

        verify(messageCipherService).encrypt(entry.metadata.userId, entry.message, defaultConnectionTag)
    }

    @Test
    fun `it should process messages when a relay connection is available and no messages are currently queued`() {
        val entry = randomSenderMessageEntry()

        val sender = createSender(true)

        sender.addToQueue(entry.metadata, entry.message).get()

        verify(messageCipherService).encrypt(entry.metadata.userId, entry.message, defaultConnectionTag)
    }

    @Test
    fun `it should queue messages when the relay is online and a message is already being processed`() {
        val first = randomSenderMessageEntry()
        val sender = createSender(true, listOf(first))

        val second = randomSenderMessageEntry()
        sender.addToQueue(second).get()

        verify(messageQueuePersistenceManager).add(second)

        verify(messageCipherService, times(1)).encrypt(first.metadata.userId, first.message, defaultConnectionTag)
    }

    //XXX these tests kinda bypass the encryption result part, so kinda nasty since it relies on a specific impl detail
    @Test
    fun `it should remove the package from the sent queue once a message has been acknowledged as delivered`() {
        val sender = createSender(true)

        val entry = randomSenderMessageEntry()

        val metadata = entry.metadata

        sender.addToQueue(metadata, entry.message).get()

        relayEvents.onNext(ServerReceivedMessage(metadata.userId, metadata.messageId, currentTimestamp()))

        verify(messageQueuePersistenceManager).remove(metadata.userId, metadata.messageId)
    }

    @Test
    fun `it should process the next queued message once a successful send result has been received`() {
        val sender = createSender(true)

        val first = randomSenderMessageEntry()
        val second = randomSenderMessageEntry()

        sender.addToQueue(first.metadata, first.message).get()
        sender.addToQueue(second.metadata, second.message).get()

        relayEvents.onNext(ServerReceivedMessage(first.metadata.userId, first.metadata.messageId, currentTimestamp()))

        val order = inOrder(messageCipherService)

        order.verify(messageCipherService).encrypt(eq(first.metadata.userId), any(), eq(defaultConnectionTag))
        order.verify(messageCipherService).encrypt(eq(second.metadata.userId), any(), eq(defaultConnectionTag))
    }

    @Test
    fun `it should send the encrypted message to the relay upon receiving a successful encryption result`() {
        val sender = createSender(true)

        val entry = randomSenderMessageEntry()
        val metadata = entry.metadata
        val recipient = metadata.userId

        val messageData = MessageData(1, 1, randomEncryptedPayload())
        val result = EncryptionResult(
            listOf(messageData),
            defaultConnectionTag
        )

        whenever(messageCipherService.encrypt(any(), any(), any())).thenResolve(result)

        sender.addToQueue(metadata, entry.message).get()

        val relayUserMessage = RelayUserMessage(messageData.deviceId, messageData.registrationId, messageData.payload)
        val relayMessageBundle = RelayMessageBundle(listOf(relayUserMessage))

        verify(relayClientManager).sendMessage(defaultConnectionTag, recipient, relayMessageBundle, metadata.messageId)
    }

    @Test
    fun `it should emit a message update event once a message has been acknowledged as delivered`() {
        val sender = createSender(true)

        val entry = randomSenderMessageEntry()
        val metadata = entry.metadata
        val recipient = metadata.userId

        val testSubscriber = sender.messageSent.testSubscriber()

        sender.addToQueue(metadata, entry.message).get()

        val timestamp = currentTimestamp()
        val record = MessageSendRecord(metadata, timestamp)
        relayEvents.onNext(ServerReceivedMessage(recipient, metadata.messageId, timestamp))

        assertEventEmitted(testSubscriber) {
            assertEquals(record, it, "Invalid message send record")
        }
    }

    //used for self messages when no other devices are available
    @Test
    fun `it should emit a message update event if no encrypted messages are returned from MessageCipherService`() {
        val sender = createSender(true)

        val entry = randomSenderMessageEntry()
        val metadata = entry.metadata

        val testSubscriber = sender.messageSent.testSubscriber()

        val result = EncryptionResult(
            emptyList(),
            defaultConnectionTag
        )

        whenever(messageCipherService.encrypt(any(), any(), any())).thenResolve(result)

        sender.addToQueue(metadata, entry.message).get()

        val record = MessageSendRecord(metadata, currentTimestamp())

        assertEventEmitted(testSubscriber) {
            assertEquals(record.metadata, it.metadata, "Invalid message metadata")
        }
    }

    @Test
    fun `it should clear the message queue on a relay disconnect`() {
        val entry = randomSenderMessageEntry()

        val sender = createSender(true, listOf(entry))

        reset(messageCipherService)
        whenever(messageQueuePersistenceManager.getUndelivered()).thenResolve(emptyList())

        setRelayOnlineStatus(false)
        setRelayOnlineStatus(true)

        verify(messageCipherService, never()).encrypt(any(), any(), any())
    }

    @Test
    fun `it should discard encrypted messages when the relay is now offline`() {
        val entry = randomSenderMessageEntry()

        val d = deferred<EncryptionResult, Exception>()

        whenever(messageCipherService.encrypt(any(), any(), any())).thenReturn(d.promise)

        val sender = createSender(true, listOf(entry))

        setRelayOnlineStatus(false)

        d.resolve(randomEncryptionResult())

        verify(relayClientManager, never()).sendMessage(any(), any(), any(), any())
    }

    @Test
    fun `it should call MessageCipherService to do a device refresh when receiving a DeviceMismatch from the relay`() {
        val sender = createSender(true)

        val entry = randomSenderMessageEntry()

        val content = DeviceMismatchContent(listOf(1), listOf(2), listOf(3))
        val to = entry.metadata.userId
        val ev = DeviceMismatch(to, entry.metadata.messageId, content)

        sender.addToQueue(entry.metadata, entry.message).get()

        whenever(messageCipherService.updateDevices(any(), any())).thenResolve(Unit)

        relayEvents.onNext(ev)

        verify(messageCipherService).updateDevices(to, content)
    }

    @Test
    fun `it should resubmit the current message for encryption after a successful device mismatch update`() {
        val sender = createSender(true)

        val entry = randomSenderMessageEntry()

        sender.addToQueue(entry.metadata, entry.message).get()

        reset(messageCipherService)
        whenever(messageCipherService.encrypt(any(), any(), any())).thenResolve(randomEncryptionResult())

        whenever(messageCipherService.updateDevices(any(), any())).thenResolve(Unit)

        val info = DeviceMismatchContent(emptyList(), emptyList(), emptyList())
        relayEvents.onNext(
            DeviceMismatch(entry.metadata.userId, randomMessageId(), info)
        )

        verify(messageCipherService).encrypt(entry.metadata.userId, entry.message, defaultConnectionTag)
    }

    @Test
    fun `addQueue(message) should propagate failures if writing to the send queue fails`() {
        val sender = createSender(true)

        val entry = randomSenderMessageEntry()

        whenever(messageQueuePersistenceManager.add(entry)).thenReject(TestException())

        assertFailsWith(TestException::class) {
            sender.addToQueue(entry.metadata, entry.message).get()
        }
    }

    @Test
    fun `addToQueue(messages) should add all given messages to the queue`() {
        val sender = createSender(false)

        val groupId = randomGroupId()

        val messages = (0..1).map {
            SenderMessageEntry(
                randomTextGroupMetadata(groupId),
                randomSerializedMessage()
            )
        }

        val members = messages.mapToSet { it.metadata.userId }

        sender.addToQueue(messages).get()

        verify(messageQueuePersistenceManager).add(messages)
    }

    @Test
    fun `addToQueue(messages) should do nothing if given an empty message list`() {
        val sender = createSender(false)

        sender.addToQueue(emptyList()).get()

        verify(messageQueuePersistenceManager, never()).add(any<Collection<SenderMessageEntry>>())
    }

    fun runWhileSending(sender: MessageSenderImpl, body: (SenderMessageEntry) -> Unit) {
        //have something occupy the current send slot
        val pendingEntry = randomSenderMessageEntry()
        sender.addToQueue(pendingEntry).get()

        body(pendingEntry)

        //complete send
        relayEvents.onNext(ServerReceivedMessage(pendingEntry.metadata.userId, pendingEntry.metadata.messageId, currentTimestamp()))
    }

    @Test
    fun `it should remove a message from its active queue when a Deleted event is received`() {
        val sender = createSender(true)

        runWhileSending(sender) {
            val entry = randomSenderMessageEntry()
            val messageId = entry.metadata.messageId
            val conversationId = entry.metadata.getConversationId()
            sender.addToQueue(entry).get()

            val event = MessageUpdateEvent.Deleted(conversationId, listOf(messageId))
            messageUpdateEvents.onNext(event)
        }

        verify(relayClientManager, times(1)).sendMessage(any(), any(), any(), any())
    }

    @Test
    fun `it should remove a message from the send queue when a Deleted event is received and the relay is online`() {
        val sender = createSender(true)

        val entry = randomSenderMessageEntry()
        val messageId = entry.metadata.messageId
        val conversationId = entry.metadata.getConversationId()

        val messageIds = listOf(messageId)

        runWhileSending(sender) {
            sender.addToQueue(entry).get()

            val event = MessageUpdateEvent.Deleted(conversationId, messageIds)
            messageUpdateEvents.onNext(event)
        }

        verify(messageQueuePersistenceManager).removeAll(conversationId, messageIds)
    }

    @Test
    fun `it should remove a message from the send queue when a Deleted event is received and the relay is offline`() {
        val sender = createSender(false)

        val entry = randomSenderMessageEntry()
        val messageId = entry.metadata.messageId
        val conversationId = entry.metadata.getConversationId()

        val messageIds = listOf(messageId)

        val event = MessageUpdateEvent.Deleted(conversationId, messageIds)
        messageUpdateEvents.onNext(event)

        verify(messageQueuePersistenceManager).removeAll(conversationId, messageIds)
    }

    @Ignore
    @Test
    fun `it should ignore a message delete if the message is currently sending`() {
        //we don't want to emit a sent event if the message happened to be deleted; else MessengerService'll pointlessly log an error when calling markMessageAsDelivered
        TODO()
    }

    @Test
    fun `it should all messages for a given conversation from its active queue when a DeletedAll event is received`() {
        val sender = createSender(true)

        runWhileSending(sender) {
            val entry = randomSenderMessageEntry()
            val conversationId = entry.metadata.getConversationId()
            sender.addToQueue(entry).get()

            val event = MessageUpdateEvent.DeletedAll(conversationId)
            messageUpdateEvents.onNext(event)
        }

        verify(relayClientManager, times(1)).sendMessage(any(), any(), any(), any())
    }

    @Test
    fun `it should all messages for a given conversation from the send queue when a DeletedAll event is received and the relay is online`() {
        val sender = createSender(true)

        val entry = randomSenderMessageEntry()
        val conversationId = entry.metadata.getConversationId()

        runWhileSending(sender) {
            sender.addToQueue(entry).get()

            val event = MessageUpdateEvent.DeletedAll(conversationId)
            messageUpdateEvents.onNext(event)
        }

        verify(messageQueuePersistenceManager).removeAllForConversation(conversationId)
    }

    @Test
    fun `it should all messages for a given conversation from the send queue when a DeletedAll event is received and the relay is offline`() {
        val sender = createSender(false)

        val conversationId = randomUserConversationId()

        val event = MessageUpdateEvent.DeletedAll(conversationId)
        messageUpdateEvents.onNext(event)

        verify(messageQueuePersistenceManager).removeAllForConversation(conversationId)
    }

    @Ignore
    @Test
    fun `it should ignore a message in a DeletedAll if the message is currently sending`() {
        TODO()
    }

    @Ignore
    @Test
    fun `retrying a send should not retry if the message was deleted`() { TODO() }
}