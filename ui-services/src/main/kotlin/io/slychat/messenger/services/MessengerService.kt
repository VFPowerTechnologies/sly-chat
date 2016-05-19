package io.slychat.messenger.services

import com.fasterxml.jackson.databind.ObjectMapper
import io.slychat.messenger.core.SlyAddress
import io.slychat.messenger.core.UserId
import io.slychat.messenger.core.currentTimestamp
import io.slychat.messenger.core.persistence.*
import io.slychat.messenger.core.randomUUID
import io.slychat.messenger.core.relay.ReceivedMessage
import io.slychat.messenger.core.relay.RelayClientEvent
import io.slychat.messenger.core.relay.ServerReceivedMessage
import io.slychat.messenger.services.crypto.*
import nl.komponents.kovenant.Promise
import nl.komponents.kovenant.deferred
import nl.komponents.kovenant.functional.map
import nl.komponents.kovenant.ui.alwaysUi
import nl.komponents.kovenant.ui.failUi
import nl.komponents.kovenant.ui.successUi
import org.slf4j.LoggerFactory
import rx.Observable
import rx.Scheduler
import rx.subjects.PublishSubject
import java.util.*

data class MessageBundle(val userId: UserId, val messages: List<MessageInfo>)
data class ContactRequest(val info: ContactInfo)

data class EncryptedMessageInfo(val messageId: String, val payload: EncryptedPackagePayloadV0)

data class QueuedSendMessage(val to: UserId, val messageInfo: MessageInfo, val connectionTag: Int)
data class QueuedReceivedMessage(val from: SlyAddress, val encryptedMessages: List<EncryptedMessageInfo>)

interface EncryptionResult
data class EncryptionOk(val encryptedMessages: List<MessageData>, val connectionTag: Int) : EncryptionResult
data class EncryptionPreKeyFetchFailure(val cause: Throwable): EncryptionResult
data class EncryptionUnknownFailure(val cause: Throwable): EncryptionResult

data class DecryptionResult(val userId: UserId, val result: MessageListDecryptionResult)

interface MessageSendResult {
    val messageId: String
}
data class MessageSendOk(val to: UserId, override val messageId: String) : MessageSendResult
//data class MessageSendDeviceMismatch() : MessageSendResult
//data class MessageSendUnknownFailure(val cause: Throwable) : MessageSendResult

//all Observerables are run on the main thread
class MessengerService(
    private val application: SlyApplication,
    private val scheduler: Scheduler,
    private val messagePersistenceManager: MessagePersistenceManager,
    private val contactsPersistenceManager: ContactsPersistenceManager,
    private val relayClientManager: RelayClientManager,
    private val messageCipherService: MessageCipherService,
    //XXX this is only used to prevent self-sends
    private val userLoginData: UserData
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

    private val sendMessageQueue = ArrayDeque<QueuedSendMessage>()
    private var currentSendMessage: QueuedSendMessage? = null

    private val receivedMessageQueue = ArrayDeque<QueuedReceivedMessage>()
    private var currentReceivedMessage: QueuedReceivedMessage? = null

    init {
        relayClientManager.events.subscribe { onRelayEvent(it) }
        relayClientManager.onlineStatus.subscribe { onRelayConnect(it) }

        messageCipherService.encryptedMessages.observeOn(scheduler).subscribe {
            processEncryptionResult(it)
        }

        messageCipherService.decryptedMessages.observeOn(scheduler).subscribe {
            processDecryptionResult(it.userId, it.result)
        }

        application.userSessionAvailable.subscribe { isAvailable ->
            if (isAvailable) {
                initializeReceiveQueue()
                messageCipherService.start()
            }
            else {
                receivedMessageQueue.clear()
                messageCipherService.shutdown()
            }
        }
    }

    private fun initializeReceiveQueue() {
        messagePersistenceManager.getQueuedPackages() successUi { packages ->
            addPackagesToReceivedQueue(packages)
        }
    }

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

                messages.forEach { addToQueue(userId, it) }
            }

            processSendMessageQueue()
        }
    }

    private fun addToQueue(userId: UserId, messageInfo: MessageInfo) {
        //once we're back online the queue'll get filled with all unsent messages
        if (!relayClientManager.isOnline)
            return

        sendMessageQueue.add(QueuedSendMessage(userId, messageInfo, relayClientManager.connectionTag))
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
                    //FIXME
                    val encryptMessages = result.encryptedMessages
                    val content = objectMapper.writeValueAsBytes(encryptMessages[0].payload)
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

    private fun markMessageAsDelivered(to: UserId, messageId: String): Promise<MessageInfo, Exception> {
        return messagePersistenceManager.markMessageAsDelivered(to, messageId) successUi { messageInfo ->
            messageUpdatesSubject.onNext(MessageBundle(to, listOf(messageInfo)))
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
                        markMessageAsDelivered(result.to, messageId) fail { e ->
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

        val message = sendMessageQueue.first

        if (message.connectionTag != relayClientManager.connectionTag) {
            log.debug("Dropping out message from send queue")
            nextSendMessage()
        }
        else {
            val messageInfo = message.messageInfo
            val textMessage = SingleUserTextMessage(messageInfo.timestamp, messageInfo.message)
            val serialized = ObjectMapper().writeValueAsBytes(textMessage)
            messageCipherService.encrypt(message.to, serialized, message.connectionTag)
            currentSendMessage = message
        }
    }

    private fun processDecryptionResult(from: UserId, result: MessageListDecryptionResult) {
        handleFailedDecryptionResults(from, result)

        val messages = result.succeeded

        val objectMapper = ObjectMapper()
        val messageStrings = messages.map {
            val message = objectMapper.readValue(it.result, SingleUserTextMessage::class.java)
            MessageInfo.newReceived(it.messageId, message.message, message.timestamp, currentTimestamp(), 0)
        }

        messagePersistenceManager.addMessages(from, messageStrings) mapUi { messageInfo ->
            val bundle = MessageBundle(from, messageInfo)
            newMessagesSubject.onNext(bundle)

            currentReceivedMessage = null
            receivedMessageQueue.pop()

            processReceivedMessageQueue()
        } fail { e ->
            log.error("Unable to store decrypted messages: {}", e.message, e)
        }
    }

    private fun processReceivedMessageQueue() {
        if (currentReceivedMessage != null)
            return

        if (receivedMessageQueue.isEmpty())
            return

        val message = receivedMessageQueue.first

        messageCipherService.decrypt(message.from, message.encryptedMessages)

        currentReceivedMessage = message
    }

    private fun onRelayEvent(event: RelayClientEvent) {
        when (event) {
            is ReceivedMessage ->
                handleReceivedMessage(event)

            is ServerReceivedMessage -> handleServerRecievedMessage(event)
        }
    }

    /** Writes the received message and then fires the new messages subject. */
    private fun writeReceivedSelfMessage(from: UserId, decryptedMessage: String): Promise<Unit, Exception> {
        val messageInfo = MessageInfo.newReceived(decryptedMessage, currentTimestamp(), 0)
        return messagePersistenceManager.addMessage(from, messageInfo) mapUi { messageInfo ->
            newMessagesSubject.onNext(MessageBundle(from, listOf(messageInfo)))
        }
    }

    private fun addPackagesToReceivedQueue(packages: List<Package>) {
        val grouped = packages.groupBy { it.id.address }
        val sortedByTimestamp = grouped.mapValues { it.value.sortedBy { it.timestamp } }

        sortedByTimestamp.map { e ->
            val encryptedMessages = e.value.map {
                val payload = deserializeEncryptedPackagePayload(it.payload)
                EncryptedMessageInfo(it.id.messageId, payload)
            }
            receivedMessageQueue.add(QueuedReceivedMessage(e.key, encryptedMessages))
            processReceivedMessageQueue()
        }
    }

    private fun handleReceivedMessage(event: ReceivedMessage) {
        val timestamp = currentTimestamp()
        //XXX this is kinda hacky...
        //the issue is that since offline messages are deserialized via jackson, using a byte array would require the
        //relay or web server to store them as base64; need to come back and fix this stuff
        val pkg = Package(PackageId(event.from, randomUUID()), timestamp, String(event.content, Charsets.UTF_8))
        val packages = listOf(pkg)

        messagePersistenceManager.addToQueue(packages) alwaysUi {
            addPackagesToReceivedQueue(packages)
        } fail { e ->
            log.error("Unable to add encrypted messages to queue: {}", e.message, e)
        }
    }

    private fun handleServerRecievedMessage(event: ServerReceivedMessage) {
        processMessageSendResult(MessageSendOk(event.to, event.messageId))
    }

    private fun handleFailedDecryptionResults(userId: UserId, result: MessageListDecryptionResult) {
        if (result.failed.isEmpty())
            return

        log.error("Unable to decrypt {} messages for {}", result.failed.size, userId)
        result.failed.forEach { log.error("Message decryption failure: {}", it.result.cause.message, it.result.cause) }

        messagePersistenceManager.removeFromQueue(userId, result.failed.map { it.messageId }).fail { e ->
            log.error("Unable to remove failed decryption packages from queue: {}", e.message, e)
        }
    }

    /* UIMessengerService interface */

    fun sendMessageTo(userId: UserId, message: String): Promise<MessageInfo, Exception> {
        val isSelfMessage = userId == userLoginData.userId

        //HACK
        //trying to send to yourself tries to use the same session for both ends, which ends up failing with a bad mac exception
        return if (!isSelfMessage) {
            val messageInfo = MessageInfo.newSent(message, 0)

            messagePersistenceManager.addMessage(userId, messageInfo) successUi { messageInfo ->
                addToQueue(userId, messageInfo)
            }
        }
        else {
            val messageInfo = MessageInfo.newSelfSent(message, 0)
            //we need to insure that the send message info is sent back to the ui before the ServerReceivedMessage is fired
            messagePersistenceManager.addMessage(userId, messageInfo) map { messageInfo ->
                Thread.sleep(30)
                messageInfo
            } successUi { messageInfo ->
                writeReceivedSelfMessage(userId, messageInfo.message)
            }
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

    fun deleteMessages(userId: UserId, messageIds: List<String>): Promise<Unit, Exception> {
        return messagePersistenceManager.deleteMessages(userId, messageIds)
    }

    fun deleteAllMessages(userId: UserId): Promise<Unit, Exception> {
        return messagePersistenceManager.deleteAllMessages(userId)
    }

    /* Other */

    fun addOfflineMessages(offlineMessages: List<Package>): Promise<Unit, Exception> {
        val d = deferred<Unit, Exception>()

        messagePersistenceManager.addToQueue(offlineMessages) successUi {
            d.resolve(Unit)

            addPackagesToReceivedQueue(offlineMessages)
        } failUi {
            d.reject(it)
        }

        return d.promise
    }
}