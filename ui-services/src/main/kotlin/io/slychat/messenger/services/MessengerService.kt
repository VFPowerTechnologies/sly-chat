package io.slychat.messenger.services

import com.fasterxml.jackson.databind.ObjectMapper
import io.slychat.messenger.core.SlyAddress
import io.slychat.messenger.core.UserId
import io.slychat.messenger.core.currentTimestamp
import io.slychat.messenger.core.persistence.*
import io.slychat.messenger.core.randomUUID
import io.slychat.messenger.core.relay.*
import io.slychat.messenger.core.relay.base.DeviceMismatchContent
import io.slychat.messenger.services.crypto.*
import nl.komponents.kovenant.Promise
import nl.komponents.kovenant.deferred
import nl.komponents.kovenant.functional.bind
import nl.komponents.kovenant.functional.map
import nl.komponents.kovenant.ui.successUi
import org.slf4j.LoggerFactory
import rx.Observable
import rx.Scheduler
import rx.subjects.PublishSubject
import rx.subscriptions.CompositeSubscription
import java.util.*

data class MessageBundle(val userId: UserId, val messages: List<MessageInfo>)

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
data class MessageSendDeviceMismatch(val to: UserId, override val messageId: String, val info: DeviceMismatchContent) : MessageSendResult
//data class MessageSendUnknownFailure(val cause: Throwable) : MessageSendResult

val List<Package>.users: Set<UserId>
    get() = mapTo(HashSet()) { it.userId }

//all Observerables are run on the main thread
class MessengerService(
    private val scheduler: Scheduler,
    private val contactsService: ContactsService,
    private val messagePersistenceManager: MessagePersistenceManager,
    private val contactsPersistenceManager: ContactsPersistenceManager,
    private val relayClientManager: RelayClientManager,
    private val messageCipherService: MessageCipherService,
    //XXX this is only used to prevent self-sends
    private val userLoginData: UserData
) {
    private val log = LoggerFactory.getLogger(javaClass)

    private val newMessagesSubject = PublishSubject.create<MessageBundle>()
    val newMessages: Observable<MessageBundle> = newMessagesSubject

    private val messageUpdatesSubject = PublishSubject.create<MessageBundle>()
    val messageUpdates: Observable<MessageBundle> = messageUpdatesSubject

    private val sendMessageQueue = ArrayDeque<QueuedSendMessage>()
    private var currentSendMessage: QueuedSendMessage? = null

    private val receivedMessageQueue = ArrayDeque<QueuedReceivedMessage>()
    private var currentReceivedMessage: QueuedReceivedMessage? = null

    private val subscriptions = CompositeSubscription()

    init {
        subscriptions.add(relayClientManager.events.subscribe { onRelayEvent(it) })
        subscriptions.add(relayClientManager.onlineStatus.subscribe { onRelayConnect(it) })

        subscriptions.add(messageCipherService.encryptedMessages.observeOn(scheduler).subscribe {
            processEncryptionResult(it)
        })

        subscriptions.add(messageCipherService.decryptedMessages.observeOn(scheduler).subscribe {
            processDecryptionResult(it.userId, it.result)
        })

        subscriptions.add(messageCipherService.deviceUpdates.observeOn(scheduler).subscribe {
            processDeviceUpdateResult(it)
        })

        subscriptions.add(contactsService.contactEvents.subscribe { onContactEvent(it) })
    }

    fun init() {
        initializeReceiveQueue()
    }

    fun shutdown() {
        receivedMessageQueue.clear()
        subscriptions.clear()
    }

    private fun onContactEvent(event: ContactEvent) {
        when (event) {
            is ContactEvent.Added -> {
                val map = event.contacts.map { it.id }.toSet()
                messagePersistenceManager.getQueuedPackages(map) successUi {
                    addPackagesToReceivedQueue(it)
                } fail { e ->
                    log.error("Unable to fetch queued packages: {}", e.message, e)
                }
            }

            is ContactEvent.InvalidContacts -> {
                log.info("Messages for invalid user ids: {}", event.contacts.map { it.long }.joinToString(","))
                messagePersistenceManager.removeFromQueue(event.contacts) fail { e ->
                    log.error("Unable to remove missing contacts: {}", e.message, e)
                }
            }

            //else do nothing; for requests the ui'll get something for it, and then call the ContactsService
        }

    }

    private fun initializeReceiveQueue() {
        messagePersistenceManager.getQueuedPackages() bind { packages ->
            contactsPersistenceManager.exists(packages.users) map { exists ->
                packages.filter { it.userId in exists }
            }
        } successUi { packages ->
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
            val messageId = message.messageInfo.id

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

    private fun retryCurrentSendMessage() {
        currentSendMessage = null
        processSendMessageQueue()
    }

    private fun nextSendMessage() {
        currentSendMessage = null
        sendMessageQueue.pop()
        processSendMessageQueue()
    }

    private fun nextReceiveMessage() {
        currentReceivedMessage = null
        receivedMessageQueue.pop()
        processReceivedMessageQueue()
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
        if (messages.isEmpty()) {
            nextReceiveMessage()
            return
        }

        val objectMapper = ObjectMapper()
        val messageStrings = messages.map {
            val message = objectMapper.readValue(it.result, SingleUserTextMessage::class.java)
            MessageInfo.newReceived(it.messageId, message.message, message.timestamp, currentTimestamp(), 0)
        }

        messagePersistenceManager.addMessages(from, messageStrings) mapUi { messageInfo ->
            val bundle = MessageBundle(from, messageInfo)
            newMessagesSubject.onNext(bundle)

            nextReceiveMessage()
        } fail { e ->
            log.error("Unable to store decrypted messages: {}", e.message, e)
        }
    }

    private fun processReceivedMessageQueue() {
        if (currentReceivedMessage != null)
            return

        if (receivedMessageQueue.isEmpty()) {
            log.debug("No more received messages")
            return
        }


        val message = receivedMessageQueue.first

        messageCipherService.decrypt(message.from, message.encryptedMessages)

        currentReceivedMessage = message
    }

    private fun onRelayEvent(event: RelayClientEvent) {
        when (event) {
            is ReceivedMessage ->
                handleReceivedMessage(event)

            is ServerReceivedMessage -> handleServerRecievedMessage(event)

            is DeviceMismatch -> handleDeviceMismatch(event)
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
            val encryptedMessages = ArrayList<EncryptedMessageInfo>()
            val failures = ArrayList<PackageId>()

            e.value.forEach { pkg ->
                try {
                    val payload = deserializeEncryptedPackagePayload(pkg.payload)
                    encryptedMessages.add(EncryptedMessageInfo(pkg.id.messageId, payload))
                }
                catch (e: Exception) {
                    log.warn("Unable to decrypt message <<{}>> from {}: {}", pkg.id.messageId, pkg.id.address.asString(), e.message, e)
                    failures.add(pkg.id)
                }
            }

            if (failures.isNotEmpty()) {
                messagePersistenceManager.removeFromQueue(failures).fail { e ->
                    log.warn("Unable to remove failed deserialized packages from queue: {}", e.message, e)
                }
            }

            if (encryptedMessages.isNotEmpty()) {
                receivedMessageQueue.add(QueuedReceivedMessage(e.key, encryptedMessages))
                processReceivedMessageQueue()
            }
        }
    }

    private fun handleReceivedMessage(event: ReceivedMessage) {
        val timestamp = currentTimestamp()
        //XXX this is kinda hacky...
        //the issue is that since offline messages are deserialized via jackson, using a byte array would require the
        //relay or web server to store them as base64; need to come back and fix this stuff
        val pkg = Package(PackageId(event.from, randomUUID()), timestamp, event.content)
        val packages = listOf(pkg)

        processPackages(packages) successUi {
            relayClientManager.sendMessageReceivedAck(event.messageId)
        }
    }

    private fun handleServerRecievedMessage(event: ServerReceivedMessage) {
        processMessageSendResult(MessageSendOk(event.to, event.messageId))
    }

    private fun handleDeviceMismatch(event: DeviceMismatch) {
        processMessageSendResult(MessageSendDeviceMismatch(event.to, event.messageId, event.info))
    }

    private fun handleFailedDecryptionResults(userId: UserId, result: MessageListDecryptionResult) {
        if (result.failed.isEmpty())
            return

        log.warn("Unable to decrypt {} messages for {}", result.failed.size, userId)
        result.failed.forEach { log.warn("Message decryption failure: {}", it.result.cause.message, it.result.cause) }

        messagePersistenceManager.removeFromQueue(userId, result.failed.map { it.messageId }).fail { e ->
            log.warn("Unable to remove failed decryption packages from queue: {}", e.message, e)
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

    /** Filter out packages which belong to blocked users, etc. */
    private fun filterPackages(packages: List<Package>): Promise<List<Package>, Exception> {
        val users = packages.users
        return contactsService.allowMessagesFrom(users) map { allowedUsers ->
            val rejected = HashSet(users)
            rejected.removeAll(allowedUsers)
            if (rejected.isNotEmpty())
                log.info("Reject messages from users: {}", rejected.map { it.long })

            packages.filter { it.id.address.id in allowedUsers }
        }
    }

    private fun processPackages(packages: List<Package>): Promise<Unit, Exception> {
        return filterPackages(packages) bind { filtered ->
            messagePersistenceManager.addToQueue(filtered) success {
                val users = HashSet<UserId>()
                users.addAll(packages.map { it.id.address.id })

                contactsPersistenceManager.exists(users) successUi { exists ->
                    val missing = HashSet(users)
                    missing.removeAll(exists)

                    if (missing.isNotEmpty())
                        contactsService.doProcessUnaddedContacts()

                    val toProcess = if (missing.isNotEmpty())
                        packages.filter { exists.contains(it.id.address.id) }
                    else
                        packages

                    addPackagesToReceivedQueue(toProcess)
                } fail { e ->
                    log.error("Failed to add packages to queue: {}", e.message, e)
                }
            }
        }
    }

    /* Other */

    fun addOfflineMessages(offlineMessages: List<Package>): Promise<Unit, Exception> {
        val d = deferred<Unit, Exception>()

        processPackages(offlineMessages) success {
            d.resolve(Unit)
        } fail {
            d.reject(it)
        }

        return d.promise
    }
}
