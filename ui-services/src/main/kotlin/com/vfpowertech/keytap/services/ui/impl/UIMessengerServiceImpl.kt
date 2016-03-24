package com.vfpowertech.keytap.services.ui.impl

import com.vfpowertech.keytap.core.persistence.ContactInfo
import com.vfpowertech.keytap.core.persistence.MessageInfo
import com.vfpowertech.keytap.services.KeyTapApplication
import com.vfpowertech.keytap.services.MessageBundle
import com.vfpowertech.keytap.services.MessengerService
import com.vfpowertech.keytap.services.ui.*
import nl.komponents.kovenant.Promise
import nl.komponents.kovenant.functional.map
import org.joda.time.format.DateTimeFormat
import org.joda.time.format.DateTimeFormatter
import org.slf4j.LoggerFactory
import rx.Subscription
import java.util.*

fun MessageInfo.toUI(formatter: DateTimeFormatter): UIMessage {
    val timestamp = if (!isDelivered) null else formatter.print(timestamp)
    return UIMessage(id, isSent, timestamp, message)
}

fun ContactInfo.toUI(): UIContactDetails =
    UIContactDetails(name, phoneNumber, email, publicKey)

/** This exists for the lifetime of the application. It wraps MessengerService, which exists for the lifetime of the user session. */
class UIMessengerServiceImpl(
    private val app: KeyTapApplication
) : UIMessengerService {
    private val log = LoggerFactory.getLogger(javaClass)

    private val newMessageListeners = ArrayList<(UIMessageInfo) -> Unit>()
    private val messageStatusUpdateListeners = ArrayList<(UIMessageInfo) -> Unit>()

    private var newMessageSub: Subscription? = null
    private var messageStatusUpdateSub: Subscription? = null

    init {
        app.userSessionAvailable.subscribe { onUserSessionAvailabilityChanged(it) }
    }

    private fun onUserSessionAvailabilityChanged(isAvailable: Boolean) {
        if (isAvailable) {
            val messengerService = getMessengerServiceOrThrow()
            newMessageSub = messengerService.newMessages.subscribe { onNewMessages(it) }
            messageStatusUpdateSub = messengerService.messageUpdates.subscribe { onMessageStatusUpdate(it) }
        }
        else {
            newMessageSub?.unsubscribe()
            newMessageSub = null

            messageStatusUpdateSub?.unsubscribe()
            messageStatusUpdateSub = null
        }
    }

    private fun getMessengerServiceOrThrow(): MessengerService {
        return app.userComponent?.messengerService ?: error("No user session has been established")
    }

    //TODO locale formatting/etc
    private fun getTimestampFormatter(): DateTimeFormatter =
        DateTimeFormat.forPattern("YYYY-MM-dd HH:mm:ss")

    /** First we add to the log, then we display it to the user. */
    private fun onNewMessages(messageBundle: MessageBundle) {
        val messages = messageBundle.messages.map { it.toUI(getTimestampFormatter()) }
        notifyNewMessageListeners(UIMessageInfo(messageBundle.contactEmail, messages))
    }

    private fun onMessageStatusUpdate(messageBundle: MessageBundle) {
        val messages = messageBundle.messages.map { it.toUI(getTimestampFormatter()) }
        notifyMessageStatusUpdateListeners(UIMessageInfo(messageBundle.contactEmail, messages))
    }

    /* Interface methods. */

    override fun sendMessageTo(contact: UIContactDetails, message: String): Promise<UIMessage, Exception> {
        return getMessengerServiceOrThrow().sendMessageTo(contact.email, message) map { messageInfo ->
            UIMessage(messageInfo.id, true, null, message)
        }
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
        return getMessengerServiceOrThrow().getLastMessagesFor(contact.email, startingAt, count) map { messages ->
            val formatter = getTimestampFormatter()
            messages.map { it.toUI(formatter) }
        }
    }

    override fun getConversations(): Promise<List<UIConversation>, Exception> {
        return getMessengerServiceOrThrow().getConversations() map { convos ->
            convos.map {
                val contact = it.contact
                val info = it.info
                UIConversation(contact.toUI(), UIConversationStatus(true, info.unreadMessageCount, info.lastMessage))
            }
        }
    }

    override fun markConversationAsRead(contact: UIContactDetails): Promise<Unit, Exception> {
        return getMessengerServiceOrThrow().markConversationAsRead(contact.email)
    }

    private fun notifyNewMessageListeners(messageInfo: UIMessageInfo) {
        for (listener in newMessageListeners)
            listener(messageInfo)
    }

    private fun notifyMessageStatusUpdateListeners(messageInfo: UIMessageInfo) {
        for (listener in messageStatusUpdateListeners)
            listener(messageInfo)
    }
}
