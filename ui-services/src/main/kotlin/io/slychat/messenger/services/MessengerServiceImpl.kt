package io.slychat.messenger.services

import com.fasterxml.jackson.databind.ObjectMapper
import io.slychat.messenger.core.UserId
import io.slychat.messenger.core.currentTimestamp
import io.slychat.messenger.core.persistence.*
import io.slychat.messenger.core.randomUUID
import io.slychat.messenger.core.relay.*
import io.slychat.messenger.core.relay.base.DeviceMismatchContent
import io.slychat.messenger.services.crypto.*
import nl.komponents.kovenant.Promise
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

private fun usersFromPackages(packages: List<Package>): Set<UserId> = packages.mapTo(HashSet()) { it.userId }

//all Observerables are run on the main thread
class MessengerServiceImpl(
    scheduler: Scheduler,
    private val contactsService: ContactsService,
    private val messagePersistenceManager: MessagePersistenceManager,
    private val contactsPersistenceManager: ContactsPersistenceManager,
    private val relayClientManager: RelayClientManager,
    private val messageCipherService: MessageCipherService,
    private val messageReceiver: MessageReceiver,
    private val selfId: UserId
) : MessengerService {
    private val log = LoggerFactory.getLogger(javaClass)

    //private val newMessagesSubject = PublishSubject.create<MessageBundle>()
    override val newMessages: Observable<MessageBundle>
        get() = messageReceiver.newMessages

    private val messageUpdatesSubject = PublishSubject.create<MessageBundle>()
    override val messageUpdates: Observable<MessageBundle> = messageUpdatesSubject

    private val sendMessageQueue = ArrayDeque<QueuedSendMessage>()
    private var currentSendMessage: QueuedSendMessage? = null

    private val subscriptions = CompositeSubscription()

    init {
        subscriptions.add(relayClientManager.events.subscribe { onRelayEvent(it) })
        subscriptions.add(relayClientManager.onlineStatus.subscribe { onRelayConnect(it) })

        subscriptions.add(messageCipherService.encryptedMessages.observeOn(scheduler).subscribe {
            processEncryptionResult(it)
        })

        subscriptions.add(messageCipherService.deviceUpdates.observeOn(scheduler).subscribe {
            processDeviceUpdateResult(it)
        })

        //FIXME
        subscriptions.add(contactsService.contactEvents.subscribe { onContactEvent(it) })
    }

    override fun init() {
    }

    override fun shutdown() {
        subscriptions.clear()
    }

    private fun onContactEvent(event: ContactEvent) {
        when (event) {
            //FIXME probably just handle in receiver
            is ContactEvent.InvalidContacts -> {
                log.info("Messages for invalid user ids: {}", event.contacts.map { it.long }.joinToString(","))
                messagePersistenceManager.removeFromQueue(event.contacts) fail { e ->
                    log.error("Unable to remove missing contacts: {}", e.message, e)
                }
            }
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
            val textMessage = TextMessageWrapper(TextMessage(messageInfo.timestamp, messageInfo.message, null))
            val serialized = ObjectMapper().writeValueAsBytes(textMessage)
            messageCipherService.encrypt(message.to, serialized, message.connectionTag)
            currentSendMessage = message
        }
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
            //FIXME
            //newMessagesSubject.onNext(MessageBundle(from, listOf(messageInfo)))
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

    /** Filter out packages which belong to blocked users, etc. */
    private fun filterBlacklisted(packages: List<Package>): Promise<List<Package>, Exception> {
        val users = usersFromPackages(packages)

        return contactsService.allowMessagesFrom(users) map { allowedUsers ->
            val rejected = HashSet(users)
            rejected.removeAll(allowedUsers)
            if (rejected.isNotEmpty())
                log.info("Reject messages from users: {}", rejected.map { it.long })

            packages.filter { it.id.address.id in allowedUsers }
        }
    }

    private fun fetchMissingContactInfo(packages: List<Package>): Promise<List<Package>, Exception> {
        val users = HashSet<UserId>()
        users.addAll(packages.map { it.id.address.id })

        //TODO should probably blacklist invalid ids for a while; prevent using clients to ddos servers by
        //sending them a bunch of invalid ids constantly
        return contactsService.addMissingContacts(users) map { invalidIds ->
            packages.filter { !invalidIds.contains(it.userId) }
        } fail { e ->
            log.error("Failed to add packages to queue: {}", e.message, e)
        }
    }

    /** Filter out blocked users, fetch any missing contact info. */
    private fun preprocessPackages(packages: List<Package>): Promise<List<Package>, Exception> {
        return filterBlacklisted(packages) bind { filtered ->
            fetchMissingContactInfo(filtered)
        }
    }

    /** Drops blacklisted IDs, fetches missing contact data and passes results to MessageReceiverService. */
    private fun processPackages(packages: List<Package>): Promise<Unit, Exception> {
        //since when we receive stuff we're always online, it makes sense that we should handle the contact info fetch as well as part of this process
        //by doing so, we keep the actual decryption free of network requirements
        //XXX although we need it to add new group members anyways... just keep this here for now
        return preprocessPackages(packages) bind { filtered ->
            messageReceiver.processPackages(filtered)
        }
    }

    /* UIMessengerService interface */

    override fun sendMessageTo(userId: UserId, message: String): Promise<MessageInfo, Exception> {
        val isSelfMessage = userId == selfId

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

    override fun getLastMessagesFor(userId: UserId, startingAt: Int, count: Int): Promise<List<MessageInfo>, Exception> {
        return messagePersistenceManager.getLastMessages(userId, startingAt, count)
    }

    override fun getConversations(): Promise<List<Conversation>, Exception> {
        return contactsPersistenceManager.getAllConversations()
    }

    override fun markConversationAsRead(userId: UserId): Promise<Unit, Exception> {
        return contactsPersistenceManager.markConversationAsRead(userId)
    }

    override fun deleteMessages(userId: UserId, messageIds: List<String>): Promise<Unit, Exception> {
        return messagePersistenceManager.deleteMessages(userId, messageIds)
    }

    override fun deleteAllMessages(userId: UserId): Promise<Unit, Exception> {
        return messagePersistenceManager.deleteAllMessages(userId)
    }

    /* Other */

    override fun addOfflineMessages(offlineMessages: List<Package>): Promise<Unit, Exception> {
        return processPackages(offlineMessages)
    }
}
