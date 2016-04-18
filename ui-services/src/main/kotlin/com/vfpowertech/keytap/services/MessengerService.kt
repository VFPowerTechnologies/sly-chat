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

data class OfflineMessage(val from: UserId, val timestamp: Int, val encryptedMessage: EncryptedMessageV0)
data class MessageBundle(val userId: UserId, val messages: List<MessageInfo>)
data class ContactRequest(val info: ContactInfo)

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

    init {
        relayClientManager.events.subscribe { onRelayEvent(it) }
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
            p bind { messageInfo ->
                messageCipherService.encrypt(userId, message) map { encryptedMessage ->
                    val content = objectMapper.writeValueAsBytes(encryptedMessage)
                    relayClientManager.sendMessage(userId, content, messageInfo.id)
                    messageInfo
                }
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
                val contactEmail = entry.key
                val result = entry.value
                if (result.failed.isNotEmpty()) {
                    log.error("Unable to decrypt {} messages for {}", result.failed.size, contactEmail)
                    result.failed.map { log.error("", it) }
                }
            }

            val groupedMessages = results.mapValues { it.value.succeeded }

            messagePersistenceManager.addReceivedMessages(groupedMessages) mapUi { groupedMessageInfo ->
                val bundles = groupedMessageInfo.mapValues { e -> MessageBundle(e.key, e.value) }
                bundles.forEach {
                    newMessagesSubject.onNext(it.value)
                }
            }
        }
    }
}