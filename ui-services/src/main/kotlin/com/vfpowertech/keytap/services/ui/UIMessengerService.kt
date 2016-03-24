package com.vfpowertech.keytap.services.ui

import com.vfpowertech.jsbridge.processor.annotations.JSToJavaGenerate
import nl.komponents.kovenant.Promise

/** Responsible for all message-related functionality between contacts. */
@JSToJavaGenerate("MessengerService")
interface UIMessengerService {
    /** Attempt to send a message to a contact. */
    fun sendMessageTo(contact: UIContactDetails, message: String): Promise<UIMessage, Exception>

    /** Listener for new incoming messages. Each list will contain messages only from a single contact. */
    fun addNewMessageListener(listener: (UIMessageInfo) -> Unit)

    /** Listener for sent message status updates. */
    fun addMessageStatusUpdateListener(listener: (UIMessageInfo) -> Unit)

    /** Listener for conversation status updates. */
    fun addConversationStatusUpdateListener(listener: (UIConversation) -> Unit)

    /**
     * Listener for when another user wants to add you as a contact, and the privacy settings require user confirmation.
     *
     * It's the responsibility of the UI to present the information to the user and then add the contact afterwards if the user wishes it.
     */
    fun addNewContactRequestListener(listener: (UIContactDetails) -> Unit)

    /**
     * Retrieve the last n messages for the given contact starting backwards at the given index.
     *
     * Examples:
     *
     * getLastMessagesFor(contact, 0, 100): Returns message numbers [0, 99]
     * getLastMessagesFor(contact, 100, 100): Returns message numbers [100, 199]
     *
     * @param contact Contact.
     * @param startingAt Backwards index to start at.
     * @param count Max number of messages to retrieve.
     *
     * @return Up to count messages
     */
    fun getLastMessagesFor(contact: UIContactDetails, startingAt: Int, count: Int): Promise<List<UIMessage>, Exception>

    /**
     * @return Pairs of UIContact -> UIConversation for every available contact.
     */
    fun getConversations(): Promise<List<UIConversation>, Exception>

    /** Resets unread message count for the given contact's conversation. */
    fun markConversationAsRead(contact: UIContactDetails): Promise<Unit, Exception>
}

