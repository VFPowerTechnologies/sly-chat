package io.slychat.messenger.android.activites.services

import io.slychat.messenger.core.UserId
import io.slychat.messenger.core.persistence.ConversationMessageInfo
import io.slychat.messenger.core.persistence.UserConversation
import io.slychat.messenger.services.MessageUpdateEvent
import io.slychat.messenger.services.messaging.ConversationMessage
import nl.komponents.kovenant.Promise

interface MessengerService {

    fun fetchAllConversation (): Promise<MutableMap<UserId, UserConversation>, Exception>

    /**
     * Get the current cached conversation
     * return null if conversation is not cached
     */
    fun getAllConversation (): MutableMap<UserId, UserConversation>?

    fun getActualSortedConversation (convo: MutableMap<UserId, UserConversation>): List<UserConversation>

    fun addNewMessageListener (listener: (ConversationMessage) -> Unit)

    fun addMessageUpdateListener (listener: (MessageUpdateEvent) -> Unit)

    fun clearListeners ()

    fun fetchMessageFor (userId: UserId, from: Int, to: Int): Promise<List<ConversationMessageInfo>, Exception>

    fun sendMessageTo (userId: UserId, message: String, ttl: Long): Promise<Unit, Exception>

    fun deleteConversation (userId: UserId): Promise<Unit, Exception>

}