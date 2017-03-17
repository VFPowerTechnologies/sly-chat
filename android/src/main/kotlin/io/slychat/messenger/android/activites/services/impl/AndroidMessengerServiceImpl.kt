package io.slychat.messenger.android.activites.services.impl

import android.support.v7.app.AppCompatActivity
import io.slychat.messenger.android.AndroidApp
import io.slychat.messenger.android.activites.services.AndroidMessengerService
import io.slychat.messenger.android.activites.services.AndroidUIMessageInfo
import io.slychat.messenger.android.activites.services.RecentChatInfo
import io.slychat.messenger.core.UserId
import io.slychat.messenger.core.persistence.*
import io.slychat.messenger.services.MessageUpdateEvent
import io.slychat.messenger.services.messaging.ConversationMessage
import nl.komponents.kovenant.Promise
import nl.komponents.kovenant.functional.bind
import nl.komponents.kovenant.functional.map
import rx.Subscription

class AndroidMessengerServiceImpl(activity: AppCompatActivity): AndroidMessengerService {
    private val app = AndroidApp.get(activity)
    private var usercomponent = app.getUserComponent()
    private val messageService = usercomponent.messageService
    private val contactService = AndroidContactServiceImpl(activity)

    private var newMessageUIListener: ((ConversationMessage) -> Unit)? = null
    private var messageUpdateUIListener: ((MessageUpdateEvent) -> Unit)? = null
    private var newMessageListener: Subscription? = null
    private var messageUpdateListener: Subscription? = null

    private val messengerService = usercomponent.messengerService

    override fun fetchAllConversation (): Promise<MutableMap<UserId, UserConversation>, Exception> {
        val conversations = mutableMapOf<UserId, UserConversation>()
        return messageService.getAllUserConversations() map { convo ->
            convo.forEach {
                conversations.put(it.contact.id, it)
            }
            conversations
        }
    }

    override fun getUserConversation(userId: UserId): Promise<UserConversation?, Exception> {
        return messageService.getUserConversation(userId)
    }

    override fun getGroupConversation(groupId: GroupId): Promise<GroupConversation?, Exception> {
        return messageService.getGroupConversation(groupId)
    }

    fun fetchAllRecentChat(): Promise<List<RecentChatInfo>, Exception> {
        val groupConversations = mutableMapOf<GroupId, GroupConversation>()
        val conversations = mutableMapOf<UserId, UserConversation>()
        val contactList = mutableMapOf<UserId, ContactInfo>()
        val list = mutableListOf<RecentChatInfo>()

        return contactService.getAll() map { c ->
            c.forEach {
                contactList.put(it.id, it)
            }
        } bind {
            messageService.getAllGroupConversations() map { groupConvo ->
                groupConvo.forEach {
                    groupConversations.put(it.group.id, it)
                    val conversationId = ConversationId.invoke(it.group.id)
                    val info = it.info
                    val contact = contactList[info.lastSpeaker]
                    if (info.lastTimestamp != null) {
                        val contactName: String
                        if(contact == null)
                            contactName = "You"
                        else
                            contactName = contact.name

                        list.add(RecentChatInfo(
                                conversationId,
                                it.group.name,
                                contactName,
                                info.lastTimestamp as Long,
                                info.lastMessage,
                                info.unreadMessageCount
                        ))
                    }
                }
            }
        } bind {
                messageService.getAllUserConversations() map { convo ->
                    convo.forEach {
                        conversations.put(it.contact.id, it)
                        val conversationId = ConversationId.invoke(it.contact.id)
                        val info = it.info
                        if (info.lastTimestamp != null)
                            list.add(RecentChatInfo(
                                    conversationId,
                                    null,
                                    it.contact.name,
                                    info.lastTimestamp as Long,
                                    info.lastMessage,
                                    info.unreadMessageCount
                            ))
                    }
                    list.sortedByDescending { it.lastTimestamp }
                }
        }
    }

    override fun getActualSortedConversation (convo: MutableMap<UserId, UserConversation>): List<UserConversation> {
        val list = mutableListOf<UserConversation>()
        convo.forEach {
            if (it.value.info.lastTimestamp !== null)
                list.add(it.value)
        }

        return list.sortedByDescending { it.info.lastTimestamp }
    }

    override fun getSortedByNameConversation (convo: MutableMap<UserId, UserConversation>): List<UserConversation> {
        val list = mutableListOf<UserConversation>()
        convo.forEach {
            list.add(it.value)
        }

        return list.sortedBy { it.contact.name }
    }

    override fun addNewMessageListener (listener: (ConversationMessage) -> Unit) {
        newMessageListener?.unsubscribe()
        newMessageListener = messageService.newMessages.subscribe {
            notifyNewMessage(it)
        }
        newMessageUIListener = listener
    }

    override fun addMessageUpdateListener (listener: (MessageUpdateEvent) -> Unit) {
        messageUpdateUIListener = listener
        messageUpdateListener?.unsubscribe()
        messageUpdateListener = messageService.messageUpdates.subscribe {
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

    override fun fetchMessageFor (conversationId: ConversationId, from: Int, to: Int): Promise<List<AndroidUIMessageInfo>, Exception> {
        return messageService.getLastMessages(conversationId, from, to) map {
            val uiMessages = mutableListOf<AndroidUIMessageInfo>()
            it.forEach {
                uiMessages.add(AndroidUIMessageInfo(it))
            }

            uiMessages
        }
    }

    override fun sendMessageTo (conversationId: ConversationId, message: String, ttl: Long): Promise<Unit, Exception> {
        when(conversationId) {
            is ConversationId.User -> {
                return messengerService.sendMessageTo(conversationId.id, message, ttl)
            }
            is ConversationId.Group -> {
                return messengerService.sendGroupMessageTo(conversationId.id, message, ttl)
            }
        }
    }

    override fun deleteConversation(conversationId: ConversationId): Promise<Unit, Exception> {
        return messageService.deleteAllMessages(conversationId)
    }

    override fun startMessageExpiration(conversationId: ConversationId, messageId: String): Promise<Unit, Exception> {
        return messageService.startMessageExpiration(conversationId, messageId)
    }

    override fun deleteMessage(conversationId: ConversationId, messageId: String): Promise<Unit, Exception> {
        return messageService.deleteMessages(conversationId, listOf(messageId), false)
    }

    private fun notifyNewMessage(info: ConversationMessage) {
        newMessageUIListener?.invoke(info)
    }
}