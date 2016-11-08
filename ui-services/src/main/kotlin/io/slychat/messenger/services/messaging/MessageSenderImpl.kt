package io.slychat.messenger.services.messaging

import io.slychat.messenger.core.UserId
import io.slychat.messenger.core.condError
import io.slychat.messenger.core.crypto.randomUUID
import io.slychat.messenger.core.enforceExhaustive
import io.slychat.messenger.core.isNotNetworkError
import io.slychat.messenger.core.persistence.MessageMetadata
import io.slychat.messenger.core.persistence.MessageQueuePersistenceManager
import io.slychat.messenger.core.persistence.MessageSendFailure
import io.slychat.messenger.core.persistence.SenderMessageEntry
import io.slychat.messenger.core.relay.*
import io.slychat.messenger.core.relay.base.DeviceMismatchContent
import io.slychat.messenger.services.*
import io.slychat.messenger.services.crypto.MessageCipherService
import io.slychat.messenger.services.crypto.NoKeyDataException
import nl.komponents.kovenant.Promise
import nl.komponents.kovenant.functional.map
import nl.komponents.kovenant.ui.failUi
import nl.komponents.kovenant.ui.successUi
import org.slf4j.LoggerFactory
import rx.Observable
import rx.subjects.PublishSubject
import rx.subscriptions.CompositeSubscription
import java.util.*

class MessageSenderImpl(
    private val messageCipherService: MessageCipherService,
    private val relayClientManager: RelayClientManager,
    private val messageQueuePersistenceManager: MessageQueuePersistenceManager,
    private val relayClock: RelayClock,
    messageUpdates: Observable<MessageUpdateEvent>
) : MessageSender {
    /** Result of sending a message via the relay. */
    private sealed class MessageSendResult {
        abstract val relayMessageId: String

        class Ok(val to: UserId, override val relayMessageId: String, val timestamp: Long) : MessageSendResult()
        class DeviceMismatch(val to: UserId, override val relayMessageId: String, val info: DeviceMismatchContent) : MessageSendResult()
        class InactiveUser(val to: UserId, override val relayMessageId: String) : MessageSendResult()
    }

    private class QueuedSendMessage(
        val relayMessageId: String,
        val metadata: MessageMetadata,
        val serialized: ByteArray,
        val connectionTag: Int
    )

    private val log = LoggerFactory.getLogger(javaClass)

    private val messageSentSubject = PublishSubject.create<MessageSendRecord>()
    override val messageSent: Observable<MessageSendRecord>
        get() = messageSentSubject

    private val sendMessageQueue = ArrayDeque<QueuedSendMessage>()
    private var currentSendMessage: QueuedSendMessage? = null

    private val subscriptions = CompositeSubscription()

    init {
        subscriptions.add(relayClientManager.events.subscribe { onRelayEvent(it) })
        subscriptions.add(relayClientManager.onlineStatus.subscribe { onRelayOnlineStatus(it) })
        subscriptions.add(messageUpdates.subscribe { onMessageUpdateEvent(it) })
    }

    internal val currentRelayMessageId: String?
        get() = currentSendMessage?.relayMessageId

    private fun onMessageUpdateEvent(event: MessageUpdateEvent) = when (event) {
        is MessageUpdateEvent.Deleted -> onMessagesDeleted(event)
        is MessageUpdateEvent.DeletedAll -> onAllMessagesDeleted(event)
        else -> {}
    }

    private fun onMessagesDeleted(event: MessageUpdateEvent.Deleted) {
        sendMessageQueue.removeAll {
            it.metadata.getConversationId() == event.conversationId && event.messageIds.contains(it.metadata.messageId)
        }

        messageQueuePersistenceManager.removeAll(event.conversationId, event.messageIds) fail {
            log.error("Failed to delete deleted messages from send queue: {}", it.message, it)
        }
    }

    private fun onAllMessagesDeleted(event: MessageUpdateEvent.DeletedAll) {
        sendMessageQueue.removeAll {
            it.metadata.getConversationId() == event.conversationId
        }

        messageQueuePersistenceManager.removeAllForConversation(event.conversationId) fail {
            log.error("Failed to delete deleted messages from send queue: {}", it.message, it)
        }
    }

    override fun init() {
    }

    override fun shutdown() {
        subscriptions.clear()
    }

    private fun onRelayOnlineStatus(connected: Boolean) {
        if (!connected) {
            clearQueue()
            return
        }

        messageQueuePersistenceManager.getUndelivered() successUi { undelivered ->
            undelivered.forEach { e ->
                addToQueueReal(e.metadata, e.message)
            }
        }
    }

    private fun onRelayEvent(event: RelayClientEvent) {
        when (event) {
            is ServerReceivedMessage -> handleServerRecievedMessage(event)

            is DeviceMismatch -> handleDeviceMismatch(event)

            is InactiveUser -> handleInactiveUser(event)
        }
    }

    private fun clearQueue() {
        sendMessageQueue.clear()
        currentSendMessage = null
    }

    private fun retryCurrentSendMessage() {
        sendMessageQueue.addFirst(currentSendMessage)
        currentSendMessage = null
        processSendMessageQueue()
    }

    private fun nextSendMessage() {
        currentSendMessage = null
        processSendMessageQueue()
    }

    private fun processSendMessageQueue() {
        if (!relayClientManager.isOnline)
            return

        //waiting on a message
        if (currentSendMessage != null)
            return

        if (sendMessageQueue.isEmpty()) {
            log.debug("No more messages to send")
            return
        }

        val message = sendMessageQueue.first

        if (message.connectionTag != relayClientManager.connectionTag) {
            log.debug("Dropping out message from send queue")
            nextSendMessage()
        }
        else {
            currentSendMessage = message
            sendMessageQueue.pop()

            messageCipherService.encrypt(message.metadata.userId, message.serialized, message.connectionTag) successUi {
                processEncryptionSuccess(it)
            } failUi {
                processEncryptionFailure(message,it)
            }
        }
    }

    private fun removeMessageFromQueue(metadata: MessageMetadata): Promise<Unit, Exception> {
        return messageQueuePersistenceManager.remove(metadata.userId, metadata.messageId) map { Unit } fail { e ->
            log.error("Unable to remove message from send queue: {}", e.message, e)
        }
    }

    private fun processMessageSendResult(result: MessageSendResult) {
        val message = currentSendMessage

        if (message != null) {
            when (result) {
                is MessageSendResult.Ok -> handleSendOk(message, result)
                is MessageSendResult.DeviceMismatch -> handleDeviceMismatch(result)
                is MessageSendResult.InactiveUser -> handleInactiveUser(message, result)
            }.enforceExhaustive()
        }
        else {
            log.error("ProcessMessageSendResult called but currentMessage was null")
            processSendMessageQueue()
        }
    }

    private fun handleInactiveUser(message: QueuedSendMessage, result: MessageSendResult.InactiveUser) {
        log.info("User {} is no longer active", result.to)

        removeMessageFromQueue(message.metadata) bindUi {
            messageCipherService.clearDevices(result.to)
        } successUi {
            messageSentSubject.onNext(MessageSendRecord.Failure(message.metadata, MessageSendFailure.InactiveUser()))
        } fail {
            log.error("Failed to clear devices for {}: {}", result.to, it.message, it)
        }

        nextSendMessage()
    }

    private fun handleDeviceMismatch(result: MessageSendResult.DeviceMismatch) {
        log.info("Got device mismatch for user={}, relayMessageId={}", result.to, result.relayMessageId)

        messageCipherService.updateDevices(result.to, result.info) successUi {
            processDeviceUpdateSuccess()
        } failUi {
            processDeviceUpdateFailure(it)
        }
    }

    private fun handleSendOk(message: QueuedSendMessage, result: MessageSendResult.Ok) {
        removeMessageFromQueue(message.metadata) successUi {
            messageSentSubject.onNext(MessageSendRecord.Ok(message.metadata, result.timestamp))
        }

        nextSendMessage()
    }

    private fun addToQueueReal(metadata: MessageMetadata, message: ByteArray) {
        addToQueueReal(listOf(
            SenderMessageEntry(metadata, message)
        ))
    }

    private fun addToQueueReal(messages: List<SenderMessageEntry>) {
        //once we're back online the queue'll get filled with all unsent messages
        if (!relayClientManager.isOnline)
            return

        messages.forEach {
            val queuedSendMessage = QueuedSendMessage(randomUUID(), it.metadata, it.message, relayClientManager.connectionTag)

            sendMessageQueue.add(queuedSendMessage)
        }

        processSendMessageQueue()

        return
    }

    override fun addToQueue(entry: SenderMessageEntry): Promise<Unit, Exception> {
        return messageQueuePersistenceManager.add(entry) mapUi {
            addToQueueReal(entry.metadata, entry.message)
        }
    }

    override fun addToQueue(messages: List<SenderMessageEntry>): Promise<Unit, Exception> {
        if (messages.isEmpty())
            return Promise.ofSuccess(Unit)

        return messageQueuePersistenceManager.add(messages) mapUi {
            addToQueueReal(messages)
        }
    }

    private fun handleInactiveUser(event: InactiveUser) {
        processMessageSendResult(MessageSendResult.InactiveUser(event.to, event.messageId))
    }

    private fun handleServerRecievedMessage(event: ServerReceivedMessage) {
        processMessageSendResult(MessageSendResult.Ok(event.to, event.messageId, event.timestamp))
    }

    private fun handleDeviceMismatch(event: DeviceMismatch) {
        processMessageSendResult(MessageSendResult.DeviceMismatch(event.to, event.messageId, event.info))
    }

    private fun processDeviceUpdateFailure(cause: Exception) {
        log.error("Unable to update devices: {}", cause.message, cause)
        //FIXME ???
        nextSendMessage()
    }

    private fun processDeviceUpdateSuccess() {
        log.info("Device mismatch fixed")

        retryCurrentSendMessage()
    }

    private fun getCurrentSendMessage(): QueuedSendMessage? {
        //this can occur if we get disconnected during the encryption process
        //the queue'll be reset on disconnect, so just do nothing
        if (!relayClientManager.isOnline)
            return null

        val message = currentSendMessage
        //can occur if we disconnect and then receive a message, so do nothing
        if (message == null)
            log.warn("processEncryptionResult called but currentMessage was null")

        return message
    }

    private fun processEncryptionFailure(message: QueuedSendMessage, cause: Exception) {
        when (cause) {
            is NoKeyDataException -> {
                log.info("No key data found for {}", message.metadata.userId)
                handleInactiveUser(InactiveUser(message.metadata.userId, message.relayMessageId))
            }

            else -> {
                log.condError(isNotNetworkError(cause), "Unknown error during encryption: {}", cause.message, cause)
                nextSendMessage()
            }
        }
    }

    private fun processEncryptionSuccess(result: EncryptionResult) {
        val message = getCurrentSendMessage() ?: return

        val userId = message.metadata.userId
        //we don't check this against the current id as even during a disconnect the last message could also be
        //the first message to resend
        val relayMessageId = message.relayMessageId

        val messages = result.encryptedMessages.map { e ->
            RelayUserMessage(e.deviceId, e.registrationId, e.payload)
        }

        if (messages.isNotEmpty()) {
            val content = RelayMessageBundle(messages)
            //if we got disconnected while we were encrypting, just ignore the message as it'll just be encrypted again
            //sendMessage'll ignore any message without a matching connectionTag
            relayClientManager.sendMessage(result.connectionTag, userId, content, relayMessageId)
        }
        else {
            //if we have no encryptedMessages and didn't get an error, it was a message to self
            //so just act as if it was sent successfully
            handleServerRecievedMessage(ServerReceivedMessage(userId, relayMessageId, relayClock.currentTime()))
        }
    }
}