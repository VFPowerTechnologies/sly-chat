package com.vfpowertech.keytap.services

import com.vfpowertech.keytap.core.persistence.*
import com.vfpowertech.keytap.core.relay.ReceivedMessage
import com.vfpowertech.keytap.core.relay.RelayClientEvent
import com.vfpowertech.keytap.core.relay.ServerReceivedMessage
import nl.komponents.kovenant.Promise
import nl.komponents.kovenant.functional.map
import nl.komponents.kovenant.ui.successUi
import org.slf4j.LoggerFactory
import rx.Observable
import rx.subjects.PublishSubject

data class OfflineMessage(val from: String, val timestamp: Int, val message: String)
data class MessageBundle(val contactEmail: String, val messages: List<MessageInfo>)
data class ContactRequest(val info: ContactInfo)

//all Observerables are run on the main thread
class MessengerService(
    private val messagePersistenceManager: MessagePersistenceManager,
    private val contactsPersistenceManager: ContactsPersistenceManager,
    private val relayClientManager: RelayClientManager
) {
    private val log = LoggerFactory.getLogger(javaClass)

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

    private fun handleReceivedMessage(event: ReceivedMessage) {
        messagePersistenceManager.addMessage(event.from, false, event.message, 0) successUi { messageInfo ->
            newMessagesSubject.onNext(MessageBundle(event.from, listOf(messageInfo)))
        }
    }

    private fun handleServerRecievedMessage(event: ServerReceivedMessage) {
        messagePersistenceManager.markMessageAsDelivered(event.to, event.messageId) successUi { messageInfo ->
            messageUpdatesSubject.onNext(MessageBundle(event.to, listOf(messageInfo)))
        }
    }

    /* UIMessengerService interface */

    fun sendMessageTo(contactEmail: String, message: String): Promise<MessageInfo, Exception> {
        return messagePersistenceManager.addMessage(contactEmail, true, message, 0) map { messageInfo ->
            relayClientManager.sendMessage(contactEmail, messageInfo.message, messageInfo.id)
            messageInfo
        }
    }

    fun getLastMessagesFor(contactEmail: String, startingAt: Int, count: Int): Promise<List<MessageInfo>, Exception> {
        return messagePersistenceManager.getLastMessages(contactEmail, startingAt, count)
    }

    fun getConversations(): Promise<List<Conversation>, Exception> {
        return contactsPersistenceManager.getAllConversations()
    }

    fun markConversationAsRead(contactEmail: String): Promise<Unit, Exception> {
        return contactsPersistenceManager.markConversationAsRead(contactEmail)
    }

    /* Other */

    fun addOfflineMessages(offlineMessages: List<OfflineMessage>): Promise<Unit, Exception> {
        val grouped = offlineMessages.groupBy { it.from }
        val sortedByTimestamp = grouped.mapValues { it.value.sortedBy { it.timestamp } }
        val groupedMessages = sortedByTimestamp.mapValues { it.value.map { it.message } }

        return messagePersistenceManager.addReceivedMessages(groupedMessages) mapUi { groupedMessageInfo ->
            val bundles = groupedMessageInfo.mapValues { e -> MessageBundle(e.key, e.value) }
            bundles.forEach {
                newMessagesSubject.onNext(it.value)
            }
        }
    }
}