package com.vfpowertech.keytap.services.ui.dummy

import com.vfpowertech.keytap.core.UserId
import com.vfpowertech.keytap.services.ui.*
import nl.komponents.kovenant.Promise
import nl.komponents.kovenant.functional.map
import java.util.*
import kotlin.concurrent.timerTask

private fun nowTimestamp(): Long {
    val now = Date()
    return now.time
}

class DummyUIMessengerService(private val contactsService: UIContactsService) : UIMessengerService {
    override fun deleteAllMessagesFor(contact: UIContactDetails): Promise<Unit, Exception> {
        return Promise.ofSuccess(Unit)
    }

    override fun deleteMessagesFor(contact: UIContactDetails, messages: List<String>): Promise<Unit, Exception> {
        return Promise.ofSuccess(Unit)
    }

    private val timer = Timer(true)
    private val newMessageListeners = ArrayList<(UIMessageInfo) -> Unit>()
    private val messageStatusUpdateListeners = ArrayList<(UIMessageInfo) -> Unit>()
    private val conversationInfoUpdateListeners = ArrayList<(UIConversation) -> Unit>()
    private val contactRequestListeners = ArrayList<(UIContactDetails) -> Unit>()

    private val conversations = HashMap<UIContactDetails, UIConversationStatus>()
    private val messages = HashMap<UserId, MutableList<UIMessage>>()

    private fun getConversationFor(contact: UIContactDetails): UIConversationStatus = synchronized(this) {
        val maybeConvo = conversations[contact]
        if (maybeConvo != null)
            return maybeConvo
        val convo = UIConversationStatus(false, 0, null, null)
        conversations[contact] = convo
        return convo
    }

    private fun getMessagesFor(userId: UserId): MutableList<UIMessage> = synchronized(this) {
        val maybeMessages = messages[userId]
        if (maybeMessages != null)
            return maybeMessages
        val list = ArrayList<UIMessage>()
        messages[userId] = list
        //fill with dummy received messages
        val count = 19
        for (i in 0..count) {
            val n = count - i
            list.add(UIMessage(n.toString(), false, nowTimestamp(), "Message $n"))
        }
        return list
    }

    override fun addConversationStatusUpdateListener(listener: (UIConversation) -> Unit) {
        synchronized(this) {
           conversationInfoUpdateListeners.add(listener)
        }
    }

    override fun sendMessageTo(contact: UIContactDetails, message: String): Promise<UIMessage, Exception> = synchronized(this) {
        val messages = getMessagesFor(contact.id)
        val id = messages.size.toString()
        val newMessage = UIMessage(id, true, null, message)
        messages.add(0, newMessage)
        //simulate send delay
        timer.schedule(timerTask {
            val timestamp = nowTimestamp()
            val withTimestamp = newMessage.copy(timestamp = timestamp)
            val idx = messages.indexOf(newMessage)
            messages[idx] = withTimestamp
            notifyMessageStatusUpdateListeners(contact.id, withTimestamp)
        }, 1000)
        return Promise.ofSuccess(newMessage)
    }

    override fun addNewMessageListener(listener: (UIMessageInfo) -> Unit) {
        synchronized(this) {
            newMessageListeners.add(listener)
        }
    }

    fun receiveNewMessage(userId: UserId, messageText: String) {
        synchronized(this) {
            val messages = getMessagesFor(userId)
            val id = messages.size.toString()
            val message = UIMessage(id, false, null, messageText)
            val messageInfo = UIMessageInfo(userId, listOf(message))
            messages.add(0, message)
            notifyNewMessageListeners(messageInfo)
        }
    }

    private fun notifyNewMessageListeners(messageInfo: UIMessageInfo) {
        synchronized(this) {
            for (listener in newMessageListeners)
                listener(messageInfo)
        }
    }

    private fun notifyMessageStatusUpdateListeners(userId: UserId, message: UIMessage) {
        synchronized(this) {
            for (listener in messageStatusUpdateListeners)
                listener(UIMessageInfo(userId, listOf(message)))
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
        val messages = getMessagesFor(contact.id)
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