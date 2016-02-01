package com.vfpowertech.keytap.ui.services.impl

import com.vfpowertech.keytap.ui.services.ContactsService
import com.vfpowertech.keytap.ui.services.MessengerService
import com.vfpowertech.keytap.ui.services.UIContactInfo
import com.vfpowertech.keytap.ui.services.UIConversation
import com.vfpowertech.keytap.ui.services.UIConversationInfo
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

    private val conversations = HashMap<UIContactInfo, UIConversationInfo>()
    private val messages = HashMap<UIContactInfo, MutableList<UIMessage>>()

    private fun getConversationFor(contact: UIContactInfo): UIConversationInfo = synchronized(this) {
        val maybeConvo = conversations[contact]
        if (maybeConvo != null)
            return maybeConvo
        val convo = UIConversationInfo(0, null)
        conversations[contact] = convo
        return convo
    }

    private fun getMessagesFor(contact: UIContactInfo): MutableList<UIMessage> = synchronized(this) {
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

    override fun sendMessageTo(contact: UIContactInfo, message: String): Promise<UIMessage, Exception> = synchronized(this) {
        val messages = getMessagesFor(contact)
        val id = messages.size
        val newMessage = UIMessage(id, true, null, message)
        messages.add(newMessage)
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

    private fun notifyMessageStatusUpdateListeners(contact: UIContactInfo, message: UIMessage) {
        synchronized(this) {
            println("here")
            for (listener in messageStatusUpdateListeners)
                listener(UIMessageInfo(contact, message))
        }
    }

    override fun getLastMessagesFor(contact: UIContactInfo, startingAt: Int, count: Int): Promise<List<UIMessage>, Exception> = synchronized(this) {
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