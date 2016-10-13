package io.slychat.messenger.services.ui

import com.vfpowertech.jsbridge.processor.annotations.Exclude
import com.vfpowertech.jsbridge.processor.annotations.JSToJavaGenerate
import io.slychat.messenger.core.UserId
import io.slychat.messenger.core.persistence.GroupId
import nl.komponents.kovenant.Promise

/** Responsible for all message-related functionality between contacts. */
@JSToJavaGenerate("MessengerService")
interface UIMessengerService {
    /** Attempt to send a message to a contact. */
    fun sendMessageTo(userId: UserId, message: String, ttlMs: Long): Promise<Unit, Exception>

    fun sendGroupMessageTo(groupId: GroupId, message: String, ttlMs: Long): Promise<Unit, Exception>

    /** Listener for new incoming messages. Each list will contain messages only from a single contact. */
    fun addNewMessageListener(listener: (UIMessageInfo) -> Unit)

    /** Listener for sent message status updates. */
    fun addMessageStatusUpdateListener(listener: (UIMessageUpdateEvent) -> Unit)

    fun addConversationInfoUpdateListener(listener: (UIConversationDisplayInfo) -> Unit)

    fun addClockDifferenceUpdateListener(listener: (Long) -> Unit)

    /**
     * Retrieve the last n messages for the given contact starting backwards at the given index.
     *
     * Examples:
     *
     * getLastMessagesFor(contact, 0, 100): Returns message numbers [0, 99]
     * getLastMessagesFor(contact, 100, 100): Returns message numbers [100, 199]
     *
     * @param userId Contact.
     * @param startingAt Backwards index to start at.
     * @param count Max number of messages to retrieve.
     *
     * @return Up to count messages
     */
    fun getLastMessagesFor(userId: UserId, startingAt: Int, count: Int): Promise<List<UIMessage>, Exception>

    /** Delete all messages for the given contact. */
    fun deleteAllMessagesFor(userId: UserId): Promise<Unit, Exception>

    /**
     * Deletes all the given messages from the given contact's conversation.
     *
     * @param userId Contact.
     * @param messages List of message IDs to delete.
     */
    fun deleteMessagesFor(userId: UserId, messages: List<String>): Promise<Unit, Exception>

    /**
     * @return Pairs of UIContact -> UIConversation for every available contact.
     */
    fun getConversations(): Promise<List<UIConversation>, Exception>

    fun getConversation(userId: UserId): Promise<UIConversation?, Exception>

    fun startMessageExpiration(userId: UserId, messageId: String): Promise<Unit, Exception>

    @Exclude
    fun clearListeners()
}

