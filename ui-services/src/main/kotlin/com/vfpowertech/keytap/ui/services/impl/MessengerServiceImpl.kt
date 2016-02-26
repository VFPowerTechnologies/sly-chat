package com.vfpowertech.keytap.ui.services.impl

import com.vfpowertech.keytap.core.persistence.ContactsPersistenceManager
import com.vfpowertech.keytap.core.relay.ReceivedMessage
import com.vfpowertech.keytap.core.relay.RelayClientEvent
import com.vfpowertech.keytap.core.relay.ServerReceivedMessage
import com.vfpowertech.keytap.ui.services.*
import com.vfpowertech.keytap.ui.services.dummy.UIMessageInfo
import nl.komponents.kovenant.Promise
import nl.komponents.kovenant.functional.map
import org.slf4j.LoggerFactory
import rx.Subscription
import java.util.*

//TODO maybe wrap the contacts persistence manager in something so it can be shared between this and the ContactService?
class MessengerServiceImpl(
    private val app: KeyTapApplication
) : MessengerService {
    private val log = LoggerFactory.getLogger(javaClass)

    private val newMessageListeners = ArrayList<(UIMessageInfo) -> Unit>()
    private val messageStatusUpdateListeners = ArrayList<(UIMessageInfo) -> Unit>()

    private var eventSub: Subscription? = null

    init {
        app.userSessionAvailable.subscribe { onUserSessionAvailabilityChanged(it) }
    }

    private fun onUserSessionAvailabilityChanged(isAvailable: Boolean) {
        if (isAvailable) {
            eventSub = getRelayClientManagerOrThrow().events.subscribe { onNext(it) }
        }
        else {
            eventSub?.unsubscribe()
            eventSub = null
        }
    }

    private fun getContactsPersistenceManagerOrThrow(): ContactsPersistenceManager {
        return app.userComponent?.contactsPersistenceManager ?: error("No user session has been established")
    }

    private fun getRelayClientManagerOrThrow(): RelayClientManager {
        return app.userComponent?.relayClientManager ?: error("No user session has been established")
    }

    private fun onNext(event: RelayClientEvent) {
        when (event) {
            is ReceivedMessage -> {
                val message = UIMessage(0, false, "time", event.message)
                notifyNewMessageListeners(UIMessageInfo(event.from, message))
            }

            is ServerReceivedMessage -> {
                //TODO
                //notifyMessageStatusUpdateListeners(event.to, UIMessage(0, false, null, ""))
            }

            else -> {
                log.warn("Unhandled RelayClientEvent: {}", event)
            }
        }
    }

    fun disconnect() {
        getRelayClientManagerOrThrow().disconnect()
    }

    /** Interface methods. */

    override fun sendMessageTo(contact: UIContactDetails, message: String): Promise<UIMessage, Exception> {
        getRelayClientManagerOrThrow().sendMessage(contact.email, message)

        return Promise.ofSuccess(UIMessage(0, true, "", message))
    }

    override fun addNewMessageListener(listener: (UIMessageInfo) -> Unit) {
        newMessageListeners.add(listener)
    }

    override fun addMessageStatusUpdateListener(listener: (UIMessageInfo) -> Unit) {
        messageStatusUpdateListeners.add(listener)
    }

    override fun addConversationStatusUpdateListener(listener: (UIConversation) -> Unit) {
        log.debug("addConversationStatusUpdateListener: TODO")
    }

    override fun addNewContactRequestListener(listener: (UIContactDetails) -> Unit) {
        log.debug("addNewContactRequestListener: TODO")
    }

    override fun getLastMessagesFor(contact: UIContactDetails, startingAt: Int, count: Int): Promise<List<UIMessage>, Exception> {
        return Promise.ofSuccess(listOf())
    }

    override fun getConversations(): Promise<List<UIConversation>, Exception> {
        //TODO
        val contactsPersistenceManager = getContactsPersistenceManagerOrThrow()
        return contactsPersistenceManager.getAll() map { contacts ->
            contacts.map { contact ->
                val details = UIContactDetails(contact.name, contact.phoneNumber, contact.email, contact.publicKey)
                UIConversation(details, UIConversationStatus(true, 0, null))
            }
        }
    }

    override fun markConversationAsRead(contact: UIContactDetails): Promise<Unit, Exception> {
        log.debug("markConversationAsRead: TODO")
        return Promise.ofSuccess(Unit)
    }

    private fun notifyNewMessageListeners(messageInfo: UIMessageInfo) {
        for (listener in newMessageListeners)
            listener(messageInfo)
    }

    private fun notifyMessageStatusUpdateListeners(contactEmail: String, message: UIMessage) {
        for (listener in messageStatusUpdateListeners)
            listener(UIMessageInfo(contactEmail, message))
    }
}