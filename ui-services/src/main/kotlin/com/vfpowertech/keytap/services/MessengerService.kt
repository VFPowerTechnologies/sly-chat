package com.vfpowertech.keytap.services

import com.fasterxml.jackson.databind.ObjectMapper
import com.vfpowertech.keytap.core.UserId
import com.vfpowertech.keytap.core.persistence.*
import com.vfpowertech.keytap.core.relay.ReceivedMessage
import com.vfpowertech.keytap.core.relay.RelayClientEvent
import com.vfpowertech.keytap.core.relay.ServerReceivedMessage
import com.vfpowertech.keytap.services.crypto.EncryptedMessageV0
import com.vfpowertech.keytap.services.crypto.MessageCipherService
import com.vfpowertech.keytap.services.crypto.MessageListDecryptionResult
import com.vfpowertech.keytap.services.crypto.deserializeEncryptedMessage
import nl.komponents.kovenant.Promise
import nl.komponents.kovenant.functional.bind
import nl.komponents.kovenant.functional.map
import nl.komponents.kovenant.ui.failUi
import nl.komponents.kovenant.ui.successUi
import org.slf4j.LoggerFactory
import rx.Observable
import rx.subjects.PublishSubject
import java.util.*

data class OfflineMessage(val from: UserId, val timestamp: Int, val encryptedMessage: EncryptedMessageV0)
data class MessageBundle(val userId: UserId, val messages: List<MessageInfo>)
data class ContactRequest(val info: ContactInfo)

data class QueuedMessage(val to: UserId, val messageInfo: MessageInfo)
data class QueuedReceivedMessage(val from: UserId, val encryptedMessages: List<EncryptedMessageV0>)

interface EncryptionResult
data class EncryptionOk(val encryptedMessage: EncryptedMessageV0, val connectionTag: Int) : EncryptionResult
data class EncryptionPreKeyFetchFailure(val cause: Throwable): EncryptionResult
data class EncryptionUnknownFailure(val cause: Throwable): EncryptionResult

interface MessageSendResult {
    val messageId: String
}
data class MessageSendOk(val to: UserId, override val messageId: String) : MessageSendResult
//data class MessageSendDeviceMismatch() : MessageSendResult
//data class MessageSendUnknownFailure(val cause: Throwable) : MessageSendResult

//all Observerables are run on the main thread
class MessengerService(
    private val messagePersistenceManager: MessagePersistenceManager,
    private val contactsPersistenceManager: ContactsPersistenceManager,
    private val relayClientManager: RelayClientManager,
    private val messageCipherService: MessageCipherService,
    //XXX this is only used to prevent self-sends
    private val userLoginData: UserLoginData
) {
    private val log = LoggerFactory.getLogger(javaClass)

    private val objectMapper = ObjectMapper()

    private val newMessagesSubject = PublishSubject.create<MessageBundle>()
    val newMessages: Observable<MessageBundle> = newMessagesSubject

    private val messageUpdatesSubject = PublishSubject.create<MessageBundle>()
    val messageUpdates: Observable<MessageBundle> = messageUpdatesSubject

    //TODO
    private val contactRequestsSubject = PublishSubject.create<List<ContactRequest>>()
    val contactRequests: Observable<List<ContactRequest>> = contactRequestsSubject

    private val sendMessageQueue = ArrayDeque<QueuedMessage>()
    private var currentSendMessage: QueuedMessage? = null

    private val receivedMessageQueue = ArrayDeque<QueuedReceivedMessage>()
    private var currentReceivedMessage: QueuedReceivedMessage? = null

    init {
        relayClientManager.events.subscribe { onRelayEvent(it) }
        relayClientManager.onlineStatus.subscribe { onRelayConnect(it) }
    }

    //Ship all undelivered messages
    //TODO optimize this
    private fun onRelayConnect(connected: Boolean) {
        if (!connected) {
            sendMessageQueue.clear()
            currentSendMessage = null
            return
        }

        messagePersistenceManager.getUndeliveredMessages() successUi { undelivered ->
            undelivered.forEach { e ->
                val userId = e.key
                val messages = e.value

                messages.forEach { sendMessageQueue.add(QueuedMessage(userId, it)) }
            }

            processSendMessageQueue()
        }
    }

    private fun addToQueue(userId: UserId, messageInfo: MessageInfo) {
        sendMessageQueue.add(QueuedMessage(userId, messageInfo))
        processSendMessageQueue()
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
            val messageId = message.messageInfo.id

            when (result) {
                is EncryptionOk -> {
                    //if we got disconnected while we were encrypting, just ignore the message as it'll just be encrypted again
                    //sendMessage'll ignore any message without a matching connectionTag
                    val content = objectMapper.writeValueAsBytes(result.encryptedMessage)
                    relayClientManager.sendMessage(result.connectionTag, userId, content, messageId)
                }

                is EncryptionUnknownFailure -> {
                    log.error("Unknown error during encryption: {}", result.cause.message, result.cause)
                    processSendMessageQueue()
                }

                else -> throw RuntimeException("Unknown result: $result")
            }
        }
        else {
            //can occur if we disconnect and then receive a message, so do nothing
            log.warn("processEncryptionResult called but currentMessage was null")
        }
    }

    private fun processMessageSendResult(result: MessageSendResult) {
        val message = currentSendMessage

        if (message != null) {
            val messageId = message.messageInfo.id

            when (result) {
                is MessageSendOk -> {
                    //this should never happen; nfi what to do if it does? try to send again?
                    //no idea what would cause this either
                    if (result.messageId != messageId) {
                        log.error("Message mismatch")
                    }
                    else {
                        val to = result.to
                        messagePersistenceManager.markMessageAsDelivered(to, messageId) successUi { messageInfo ->
                            messageUpdatesSubject.onNext(MessageBundle(to, listOf(messageInfo)))
                        } fail { e ->
                            log.error("Unable to write message to log: {}", e.message, e)
                        }
                    }

                    nextSendMessage()
                }

                //TODO on device mismatch, handle mismatch then process queue again

                //TODO failures
                else -> throw RuntimeException("Unknown message send result: $result")
            }
        }
        else {
            log.error("ProcessMessageSendResult called but currentMessage was null")
            processSendMessageQueue()
        }
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

        if (sendMessageQueue.isEmpty())
            return

        val connectionTag = relayClientManager.connectionTag

        val message = sendMessageQueue.first
        messageCipherService.encrypt(message.to, message.messageInfo.message) successUi { encryptedMessage ->
            processEncryptionResult(EncryptionOk(encryptedMessage, connectionTag))
        } failUi { e ->
            processEncryptionResult(EncryptionUnknownFailure(e))
        }
        currentSendMessage = message
    }

    private fun processDecryptionResult(from: UserId, result: MessageListDecryptionResult): Promise<Unit, Exception> {
        logFailedDecryptionResults(from, result)

        val messages = if (result.succeeded.isNotEmpty())
            hashMapOf(from to result.succeeded)
        else
            hashMapOf()

        return messagePersistenceManager.addReceivedMessages(messages) mapUi { groupedMessageInfo ->
            val bundles = groupedMessageInfo.mapValues { e -> MessageBundle(e.key, e.value) }
            bundles.forEach {
                newMessagesSubject.onNext(it.value)
            }

            currentReceivedMessage = null
            receivedMessageQueue.pop()

            processReceivedMessageQueue()
        }
    }

    private fun processReceivedMessageQueue() {
        if (currentReceivedMessage != null)
            return

        if (receivedMessageQueue.isEmpty())
            return

        val message = receivedMessageQueue.first

        messageCipherService.decryptMessagesForUser(message.from, message.encryptedMessages) bindUi { result ->
            processDecryptionResult(message.from, result)
        } fail { e ->
            log.error("Error during received message handling: {}", e.message, e)
        }

        currentReceivedMessage = message
    }

    private fun onRelayEvent(event: RelayClientEvent) {
        when (event) {
            is ReceivedMessage ->
                handleReceivedMessage(event)

            is ServerReceivedMessage -> handleServerRecievedMessage(event)

            else -> {
                log.warn("Unhandled RelayClientEvent: {}", event)
            }
        }
    }

    /** Writes the received message and then fires the new messages subject. */
    private fun writeReceivedMessage(from: UserId, decryptedMessage: String): Promise<Unit, Exception> {
        return messagePersistenceManager.addMessage(from, false, decryptedMessage, 0) mapUi { messageInfo ->
            newMessagesSubject.onNext(MessageBundle(from, listOf(messageInfo)))
        }
    }

    private fun handleReceivedMessage(event: ReceivedMessage) {
        val encryptedMessage = deserializeEncryptedMessage(event.content)
        receivedMessageQueue.add(QueuedReceivedMessage(event.from, listOf(encryptedMessage)))
        processReceivedMessageQueue()
    }

    private fun handleServerRecievedMessage(event: ServerReceivedMessage) {
        processMessageSendResult(MessageSendOk(event.to, event.messageId))
    }

    private fun logFailedDecryptionResults(userId: UserId, result: MessageListDecryptionResult) {
        //XXX no idea what to do here really
        if (result.failed.isNotEmpty()) {
            log.error("Unable to decrypt {} messages for {}", result.failed.size, userId)
            result.failed.forEach { log.error("Message decryption failure: {}", it.cause.message, it.cause) }
        }
    }

    /* UIMessengerService interface */

    fun sendMessageTo(userId: UserId, message: String): Promise<MessageInfo, Exception> {
        val isSelfMessage = userId == userLoginData.userId

        val p = messagePersistenceManager.addMessage(userId, true, message, 0)

        //HACK
        //trying to send to yourself tries to use the same session for both ends, which ends up failing with a bad mac exception
        return if (!isSelfMessage) {
            p successUi { messageInfo ->
                addToQueue(userId, messageInfo)
            }
        }
        else {
            //we need to insure that the send message info is sent back to the ui before the ServerReceivedMessage is fired
            //this is stupid but I don't feel like injecting an rx scheduler
            p map { messageInfo ->
                Thread.sleep(30)
                messageInfo
            } successUi { messageInfo ->
                handleServerRecievedMessage(ServerReceivedMessage(userId, messageInfo.id))
                writeReceivedMessage(userId, message) fail { e ->
                    log.error("Unable to write self-sent message: {}", e.message, e)
                }
            }

            p
        }
    }

    fun getLastMessagesFor(userId: UserId, startingAt: Int, count: Int): Promise<List<MessageInfo>, Exception> {
        return messagePersistenceManager.getLastMessages(userId, startingAt, count)
    }

    fun getConversations(): Promise<List<Conversation>, Exception> {
        return contactsPersistenceManager.getAllConversations()
    }

    fun markConversationAsRead(userId: UserId): Promise<Unit, Exception> {
        return contactsPersistenceManager.markConversationAsRead(userId)
    }

    /* Other */

    fun addOfflineMessages(offlineMessages: List<OfflineMessage>): Promise<Unit, Exception> {
        val grouped = offlineMessages.groupBy { it.from }
        val sortedByTimestamp = grouped.mapValues { it.value.sortedBy { it.timestamp } }
        val groupedEncryptedMessages = sortedByTimestamp.mapValues { it.value.map { it.encryptedMessage } }

        return messageCipherService.decryptMultiple(groupedEncryptedMessages) bind { results ->
            results.forEach {
                logFailedDecryptionResults(it.key, it.value)
            }

            val groupedMessages = results.mapValues { it.value.succeeded }.filter { it.value.isNotEmpty() }

            messagePersistenceManager.addReceivedMessages(groupedMessages) mapUi { groupedMessageInfo ->
                val bundles = groupedMessageInfo.mapValues { e -> MessageBundle(e.key, e.value) }
                bundles.forEach {
                    newMessagesSubject.onNext(it.value)
                }
            }
        }
    }
}