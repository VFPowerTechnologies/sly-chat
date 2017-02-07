package io.slychat.messenger.android.activites.services.impl

import android.support.v7.app.AppCompatActivity
import io.slychat.messenger.android.AndroidApp
import io.slychat.messenger.android.activites.RecentChatActivity
import io.slychat.messenger.android.activites.services.MessengerService
import io.slychat.messenger.android.activites.services.RecentChatInfo
import io.slychat.messenger.core.UserId
import io.slychat.messenger.core.persistence.*
import io.slychat.messenger.services.MessageUpdateEvent
import io.slychat.messenger.services.messaging.ConversationMessage
import nl.komponents.kovenant.Promise
import nl.komponents.kovenant.functional.bind
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
    private val contactService = usercomponent.contactsService

    var conversations: MutableMap<UserId, UserConversation> = mutableMapOf()
    var groupConversations: MutableMap<GroupId, GroupConversation> = mutableMapOf()

    private var newMessageUIListener: ((ConversationMessage) -> Unit)? = null
    private var messageUpdateUIListener: ((MessageUpdateEvent) -> Unit)? = null
    private var newMessageListener: Subscription? = null
    private var messageUpdateListener: Subscription? = null

    private var contactList: MutableMap<UserId, ContactInfo> = mutableMapOf()

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

    fun fetchAllRecentChat(): Promise<List<RecentChatInfo>, Exception> {
        groupConversations = mutableMapOf()
        conversations = mutableMapOf()
        contactList = mutableMapOf()
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

    override fun fetchMessageFor (conversationId: ConversationId, from: Int, to: Int): Promise<List<ConversationMessageInfo>, Exception> {
        return messageService.getLastMessages(conversationId, from, to)
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

    private fun updateConversationCache(info: ConversationMessage) {
        val conversationId = info.conversationId
        if (conversationId is ConversationId.User) {
            val userId = conversationId.id
            messageService.getUserConversation(userId) successUi { convo ->
                if(convo != null) {
                    conversations[userId] = convo
                    notifyNewMessage(info)
                }
            } failUi {
                log.error("Getting conversation for ${userId.long} failed")
            }
        }
        else if (conversationId is ConversationId.Group) {
            val groupId = conversationId.id
            messageService.getGroupConversation(groupId) successUi { convo ->
                if(convo != null) {
                    groupConversations[groupId] = convo
                    notifyNewMessage(info)
                }
            }
        }
    }

    fun getContactInfo(userId: UserId): ContactInfo? {
        return contactList[userId]
    }

}