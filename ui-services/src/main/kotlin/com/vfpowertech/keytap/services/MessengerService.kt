package com.vfpowertech.keytap.services

import com.fasterxml.jackson.databind.ObjectMapper
import com.vfpowertech.keytap.core.UserId
import com.vfpowertech.keytap.core.persistence.*
import com.vfpowertech.keytap.core.relay.ReceivedMessage
import com.vfpowertech.keytap.core.relay.RelayClientEvent
import com.vfpowertech.keytap.core.relay.ServerReceivedMessage
import com.vfpowertech.keytap.services.crypto.EncryptedMessageV0
import com.vfpowertech.keytap.services.crypto.MessageCipherService
import com.vfpowertech.keytap.services.crypto.deserializeEncryptedMessage
import nl.komponents.kovenant.Promise
import nl.komponents.kovenant.functional.bind
import nl.komponents.kovenant.functional.map
import nl.komponents.kovenant.ui.successUi
import org.slf4j.LoggerFactory
import rx.Observable
import rx.subjects.PublishSubject
import java.util.*

data class OfflineMessage(val from: UserId, val timestamp: Int, val encryptedMessage: EncryptedMessageV0)
data class MessageBundle(val userId: UserId, val messages: List<MessageInfo>)
data class ContactRequest(val info: ContactInfo)

class MessageQueue {
    private val queue = HashMap<UserId, ArrayList<MessageInfo>>()

    fun add(userId: UserId, messageInfo: MessageInfo) {
        val current = queue[userId]
        val q = if (current == null) {
            val q = ArrayList<MessageInfo>()
            queue[userId] = q
            q
        }
        else
            current

        q.add(messageInfo)
    }

    fun removeUser(userId: UserId) {
        queue.remove(userId)
    }

    fun forEach(body: (UserId, List<MessageInfo>) -> Unit) {
        if (queue.isEmpty())
            return

        //to allow modification while iterating
        val copy = HashMap(queue)
        copy.forEach { userId, messages -> body(userId, ArrayList(messages)) }
    }

    fun clear() {
        queue.clear()
    }

    fun isNotEmpty(): Boolean = queue.isNotEmpty()
}

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

    private val messageQueue = MessageQueue()

    init {
        relayClientManager.events.subscribe { onRelayEvent(it) }
        relayClientManager.onlineStatus.subscribe { onRelayConnect(it) }
    }

    //Ship all undelivered messages
    //TODO optimize this
    private fun onRelayConnect(connected: Boolean) {
        if (!connected) {
            messageQueue.clear()
            return
        }

        messagePersistenceManager.getUndeliveredMessages() successUi { undelivered ->
            undelivered.forEach { e ->
                val contactId = e.key
                val messages = e.value

                messages.forEach { m ->
                    messageQueue.add(contactId, m)
                }
            }

            processMessageQueue()
        }
    }

    private fun addToQueue(userId: UserId, messageInfo: MessageInfo) {
        messageQueue.add(userId, messageInfo)
        processMessageQueue()
    }

    private fun processMessageQueue() {
        if (!relayClientManager.isOnline)
            return

        val connectionTag = relayClientManager.connectionTag

        try {
            //XXX this is so nasty
            messageQueue.forEach { userId, messages ->
                messageCipherService.encryptMulti(userId, messages.map { it.message }) map { encryptedMessages ->
                    var i = 0
                    encryptedMessages.forEach { encryptedMessage ->
                        val id = messages[i].id
                        i += 1
                        val content = objectMapper.writeValueAsBytes(encryptedMessage)
                        relayClientManager.sendMessage(connectionTag, userId, content, id)
                    }
                } fail { e ->
                    log.error("Unable to send messages to {}: {}", userId, e.message, e)
                }

                messageQueue.removeUser(userId)
            }
        }
        catch (t: Throwable) {
            log.error("An error occured while sending messages: {}", t.message, t)
        }
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
        messageCipherService.decrypt(event.from, encryptedMessage) bind { message ->
            writeReceivedMessage(event.from, message)
        } fail { e ->
            log.error("Error during received message handling: {}", e.message, e)
        }
    }

    private fun handleServerRecievedMessage(event: ServerReceivedMessage) {
        messagePersistenceManager.markMessageAsDelivered(event.to, event.messageId) successUi { messageInfo ->
            messageUpdatesSubject.onNext(MessageBundle(event.to, listOf(messageInfo)))
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
            //XXX no idea what to do here really
            results.map { entry ->
                val contact = entry.key
                val result = entry.value
                if (result.failed.isNotEmpty()) {
                    log.error("Unable to decrypt {} messages for {}", result.failed.size, contact.id)
                    result.failed.forEach { log.error("Message decryption failure: {}", it.cause.message, it.cause) }
                }
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