package io.slychat.messenger.android.activites.services.impl

import android.support.v7.app.AppCompatActivity
import io.slychat.messenger.android.AndroidApp
import io.slychat.messenger.android.activites.services.MessengerService
import io.slychat.messenger.core.UserId
import io.slychat.messenger.core.persistence.ConversationId
import io.slychat.messenger.core.persistence.ConversationMessageInfo
import io.slychat.messenger.core.persistence.UserConversation
import io.slychat.messenger.services.MessageUpdateEvent
import io.slychat.messenger.services.messaging.ConversationMessage
import nl.komponents.kovenant.Promise
import nl.komponents.kovenant.functional.map
import nl.komponents.kovenant.ui.failUi
import nl.komponents.kovenant.ui.successUi
import org.slf4j.LoggerFactory
import rx.Subscription

class MessengerServiceImpl (activity: AppCompatActivity): MessengerService {
    private val log = LoggerFactory.getLogger(javaClass)

    private val app = AndroidApp.get(activity)
    private var usercomponent = app.getUserComponent()
    private val messageService = usercomponent.messageService
    var conversations: MutableMap<UserId, UserConversation> = mutableMapOf()
    private var newMessageUIListener: ((ConversationMessage) -> Unit)? = null
    private var messageUpdateUIListener: ((MessageUpdateEvent) -> Unit)? = null
    private var newMessageListener: Subscription? = null
    private var messageUpdateListener: Subscription? = null

    private val messengerService = usercomponent.messengerService

    override fun fetchAllConversation (): Promise<MutableMap<UserId, UserConversation>, Exception> {
        conversations = mutableMapOf()
        return messageService.getAllUserConversations() map { convo ->
            convo.forEach {
                conversations.put(it.contact.id, it)
            }
            conversations
        }
    }

    override fun getAllConversation (): MutableMap<UserId, UserConversation> {
        return conversations
    }

    override fun getActualSortedConversation (convo: MutableMap<UserId, UserConversation>): List<UserConversation> {
        val list = mutableListOf<UserConversation>()
        convo.forEach {
            if (it.value.info.lastTimestamp !== null)
                list.add(it.value)
        }

        return list.sortedByDescending { it.info.lastTimestamp }
    }

    override fun addNewMessageListener (listener: (ConversationMessage) -> Unit) {
        newMessageListener?.unsubscribe()
        newMessageListener = messageService.newMessages.subscribe {
            updateConversationCache(it)
        }
        newMessageUIListener = listener
    }

    override fun addMessageUpdateListener (listener: (MessageUpdateEvent) -> Unit) {
        messageUpdateUIListener = listener
        messageUpdateListener?.unsubscribe()
        messageUpdateListener = messageService.messageUpdates.subscribe() {
            listener.invoke(it)
        }
    }

    override fun clearListeners () {
        newMessageListener?.unsubscribe()
        messageUpdateListener?.unsubscribe()
        messageUpdateListener = null
        messageUpdateUIListener = null
        newMessageListener = null
        newMessageUIListener = null
    }

    override fun fetchMessageFor (userId: UserId, from: Int, to: Int): Promise<List<ConversationMessageInfo>, Exception> {
        return messageService.getLastMessages(ConversationId.Companion.invoke(userId), from, to)
    }

    override fun sendMessageTo (userId: UserId, message: String, ttl: Long): Promise<Unit, Exception> {
        return messengerService.sendMessageTo(userId, message, ttl)
    }

    override fun deleteConversation (userId: UserId): Promise<Unit, Exception> {
        return messageService.deleteAllMessages(ConversationId.Companion.invoke(userId))
    }

    private fun notifyNewMessage (info: ConversationMessage) {
        newMessageUIListener?.invoke(info)
    }

    private fun updateConversationCache (info: ConversationMessage) {
        val conversationId = info.conversationId
        if (conversationId is ConversationId.User) {
            val userId = conversationId.id
            messageService.getUserConversation(userId) successUi { convo ->
                if (convo !== null) {
                    conversations[userId] = convo
                    notifyNewMessage(info)
                }
            } failUi {
                log.debug("Getting conversation for ${userId.long} failed")
            }
        }
        else if (conversationId is ConversationId.Group) {
            // is group conversation
        }
    }

}