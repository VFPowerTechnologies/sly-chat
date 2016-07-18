package io.slychat.messenger.services

import io.slychat.messenger.core.UserId
import io.slychat.messenger.core.currentTimestamp
import io.slychat.messenger.core.persistence.MessagePersistenceManager
import io.slychat.messenger.core.persistence.MessageQueuePersistenceManager
import io.slychat.messenger.core.persistence.QueuedMessage
import io.slychat.messenger.core.relay.*
import io.slychat.messenger.services.crypto.DeviceUpdateResult
import io.slychat.messenger.services.crypto.MessageCipherService
import nl.komponents.kovenant.Promise
import nl.komponents.kovenant.ui.successUi
import org.slf4j.LoggerFactory
import rx.Observable
import rx.Scheduler
import rx.subjects.PublishSubject
import rx.subscriptions.CompositeSubscription
import java.util.*

class MessageSenderImpl(
    scheduler: Scheduler,
    private val messageCipherService: MessageCipherService,
    private val relayClientManager: RelayClientManager,
    private val messagePersistenceManager: MessagePersistenceManager,
    private val messageQueuePersistenceManager: MessageQueuePersistenceManager
) : MessageSender {
    private class QueuedSendMessage(
        val to: UserId,
        val messageId: String,
        val serialized: ByteArray,
        val connectionTag: Int
    )

    private val log = LoggerFactory.getLogger(javaClass)

    private val messageUpdatesSubject = PublishSubject.create<MessageBundle>()
    override val messageUpdates: Observable<MessageBundle> = messageUpdatesSubject

    private val sendMessageQueue = ArrayDeque<QueuedSendMessage>()
    private var currentSendMessage: QueuedSendMessage? = null

    private val subscriptions = CompositeSubscription()

    init {
        subscriptions.add(relayClientManager.events.subscribe { onRelayEvent(it) })
        subscriptions.add(relayClientManager.onlineStatus.subscribe { onRelayOnlineStatus(it) })

        subscriptions.add(messageCipherService.encryptedMessages.observeOn(scheduler).subscribe {
            processEncryptionResult(it)
        })

        subscriptions.add(messageCipherService.deviceUpdates.observeOn(scheduler).subscribe {
            processDeviceUpdateResult(it)
        })
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
                addToQueueReal(e.userId, e.messageId, e.serialized)
            }
        }
    }

    private fun onRelayEvent(event: RelayClientEvent) {
        when (event) {
            is ServerReceivedMessage -> handleServerRecievedMessage(event)

            is DeviceMismatch -> handleDeviceMismatch(event)
        }
    }

    private fun clearQueue() {
        sendMessageQueue.clear()
        currentSendMessage = null
    }

    private fun retryCurrentSendMessage() {
        currentSendMessage = null
        processSendMessageQueue()
    }

    private fun nextSendMessage() {
        currentSendMessage = null
        sendMessageQueue.pop()
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
            messageCipherService.encrypt(message.to, message.serialized, message.connectionTag)
            currentSendMessage = message
        }
    }

    private fun markMessageAsDelivered(to: UserId, messageId: String): Promise<Unit, Exception> {
        //FIXME emit event
        return messageQueuePersistenceManager.remove(to, messageId)
    }

    private fun processMessageSendResult(result: MessageSendResult) {
        val message = currentSendMessage

        if (message != null) {
            val messageId = message.messageId

            when (result) {
                is MessageSendOk -> {
                    //this should never happen; nfi what to do if it does? try to send again?
                    //no idea what would cause this either
                    if (result.messageId != messageId) {
                        log.error("Message mismatch")
                    }
                    else {
                        markMessageAsDelivered(result.to, messageId) fail { e ->
                            log.error("Unable to write message to log: {}", e.message, e)
                        }
                    }

                    nextSendMessage()
                }

                is MessageSendDeviceMismatch -> {
                    log.info("Got device mismatch for user={}, messageId={}", result.to, result.messageId)
                    messageCipherService.updateDevices(result.to, result.info)
                }

            //TODO failures
                else -> throw RuntimeException("Unknown message send result: $result")
            }
        }
        else {
            log.error("ProcessMessageSendResult called but currentMessage was null")
            processSendMessageQueue()
        }
    }

    private fun addToQueueReal(userId: UserId, messageId: String, message: ByteArray) {
        //once we're back online the queue'll get filled with all unsent messages
        if (!relayClientManager.isOnline)
            return

        val queuedSendMessage = QueuedSendMessage(userId, messageId, message, relayClientManager.connectionTag)
        sendMessageQueue.add(queuedSendMessage)
        processSendMessageQueue()
        return
    }

    override fun addToQueue(userId: UserId, messageId: String, message: ByteArray): Promise<Unit, Exception> {
        val queuedMessage = QueuedMessage(userId, messageId, currentTimestamp(), message)

        return messageQueuePersistenceManager.add(queuedMessage) mapUi {
            addToQueueReal(userId, messageId, message)
        }
    }

    private fun handleServerRecievedMessage(event: ServerReceivedMessage) {
        processMessageSendResult(MessageSendOk(event.to, event.messageId))
    }

    private fun handleDeviceMismatch(event: DeviceMismatch) {
        processMessageSendResult(MessageSendDeviceMismatch(event.to, event.messageId, event.info))
    }

    private fun processDeviceUpdateResult(result: DeviceUpdateResult) {
        val e = result.exception
        if (e != null) {
            log.error("Unable to update devices: {}", e.message, e)
            //FIXME ???
        }

        log.info("Device mismatch fixed")

        retryCurrentSendMessage()
    }

    private fun processEncryptionResult(result: EncryptionResult) {
        //this can occur if we get disconnected during the encryption process
        //the queue'll be reset on disconnect, so just do nothing
        if (!relayClientManager.isOnline)
            return

        val message = currentSendMessage

        if (message != null) {
            val userId = message.to
            //we don't check this against the current id as even during a disconnect the last message could also be
            //the first message to resend
            val messageId = message.messageId

            when (result) {
                is EncryptionOk -> {
                    val messages = result.encryptedMessages.map { e ->
                        RelayUserMessage(e.deviceId, e.registrationId, e.payload)
                    }
                    val content = RelayMessageBundle(messages)
                    //if we got disconnected while we were encrypting, just ignore the message as it'll just be encrypted again
                    //sendMessage'll ignore any message without a matching connectionTag
                    relayClientManager.sendMessage(result.connectionTag, userId, content, messageId)
                }

                is EncryptionUnknownFailure -> {
                    log.error("Unknown error during encryption: {}", result.cause.message, result.cause)
                    nextSendMessage()
                }

                else -> throw RuntimeException("Unknown result: $result")
            }
        }
        else {
            //can occur if we disconnect and then receive a message, so do nothing
            log.warn("processEncryptionResult called but currentMessage was null")
        }
    }

}