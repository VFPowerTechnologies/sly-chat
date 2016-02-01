package com.vfpowertech.keytap.ui.services.impl

import com.vfpowertech.keytap.ui.services.ContactsService
import com.vfpowertech.keytap.ui.services.MessengerService
import com.vfpowertech.keytap.ui.services.UIContactDetails
import com.vfpowertech.keytap.ui.services.UIConversation
import com.vfpowertech.keytap.ui.services.UIConversationStatus
import com.vfpowertech.keytap.ui.services.UIMessage
import nl.komponents.kovenant.Promise
import nl.komponents.kovenant.functional.map
import java.text.SimpleDateFormat
import java.util.*
import kotlin.concurrent.timerTask

class MessengerServiceImpl(private val contactsService: ContactsService) : MessengerService {
    private val timer = Timer(true)
    private val newMessageListeners = ArrayList<(UIMessageInfo) -> Unit>()
    private val messageStatusUpdateListeners = ArrayList<(UIMessageInfo) -> Unit>()
    private val conversationInfoUpdateListeners = ArrayList<(UIConversation) -> Unit>()
    private val contactRequestListeners = ArrayList<(UIContactDetails) -> Unit>()

    private val conversations = HashMap<UIContactDetails, UIConversationStatus>()
    private val messages = HashMap<UIContactDetails, MutableList<UIMessage>>()

    private fun getConversationFor(contact: UIContactDetails): UIConversationStatus = synchronized(this) {
        val maybeConvo = conversations[contact]
        if (maybeConvo != null)
            return maybeConvo
        val convo = UIConversationStatus(false, 0, null)
        conversations[contact] = convo
        return convo
    }

    private fun getMessagesFor(contact: UIContactDetails): MutableList<UIMessage> = synchronized(this) {
        val maybeMessages = messages[contact]
        if (maybeMessages != null)
            return maybeMessages
        val list = ArrayList<UIMessage>()
        messages[contact] = list
        //fill with dummy received messages
        val count = 19
        for (i in 0..count) {
            val n = count - i
            list.add(UIMessage(n, false, "YYYY-MM-DD HH:MM:SS", "Message $n"))
        }
        return list
    }

    override fun addConversationStatusUpdateListener(listener: (UIConversation) -> Unit) {
        synchronized(this) {
           conversationInfoUpdateListeners.add(listener)
        }
    }

    override fun sendMessageTo(contact: UIContactDetails, message: String): Promise<UIMessage, Exception> = synchronized(this) {
        val messages = getMessagesFor(contact)
        val id = messages.size
        val newMessage = UIMessage(id, true, null, message)
        messages.add(0, newMessage)
        println(messages)
        //simulate send delay
        timer.schedule(timerTask {
            val now = Date()
            val timestamp = SimpleDateFormat("yyyy-mm-dd HH:mm:ss").format(now)
            notifyMessageStatusUpdateListeners(contact, newMessage.copy(timestamp = timestamp))
        }, 1000)
        return Promise.ofSuccess(newMessage)
    }

    override fun addNewMessageListener(listener: (UIMessageInfo) -> Unit) {
        synchronized(this) {
            newMessageListeners.add(listener)
        }
    }

    private fun notifyMessageStatusUpdateListeners(contact: UIContactDetails, message: UIMessage) {
        synchronized(this) {
            for (listener in messageStatusUpdateListeners)
                listener(UIMessageInfo(contact, message))
        }
    }

    override fun addNewContactRequestListener(listener: (UIContactDetails) -> Unit) {
        synchronized(this) {
            contactRequestListeners.add(listener)
        }
    }

    private fun notifyConversationStatusUpdateListeners(conversation: UIConversation) {
        synchronized(this) {
            for (listener in conversationInfoUpdateListeners)
                listener(conversation)
        }
    }

    override fun markConversationAsRead(contact: UIContactDetails): Promise<Unit, Exception> {
        synchronized(this) {
            val convo = getConversationFor(contact)
            conversations[contact] = convo.copy(unreadMessageCount = 0)
        }
        return Promise.ofSuccess(Unit)
    }

    override fun getLastMessagesFor(contact: UIContactDetails, startingAt: Int, count: Int): Promise<List<UIMessage>, Exception> = synchronized(this) {
        //TODO startingAt/count
        val messages = getMessagesFor(contact)
        Promise.ofSuccess(messages)
    }

    override fun addMessageStatusUpdateListener(listener: (UIMessageInfo) -> Unit) {
        synchronized(this) {
            messageStatusUpdateListeners.add(listener)
        }
    }

    override fun getConversations(): Promise<List<UIConversation>, Exception> = synchronized(this) {
        contactsService.getContacts() map { contacts ->
            val m = ArrayList<UIConversation>()
            for (contact in contacts)
                m.add(UIConversation(contact, getConversationFor(contact)))
            m
        }
    }
}