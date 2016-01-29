package com.vfpowertech.keytap.ui.services

import com.vfpowertech.jsbridge.processor.annotations.JSToJavaGenerate
import nl.komponents.kovenant.Promise

/** Responsible for all message-related functionality between contacts. */
@JSToJavaGenerate
interface MessengerService {
    /** Attempt to send a message to a contact. */
    fun sendMessageTo(contactName: String, message: String): Promise<Unit, Exception>

    /** Listener for new incoming messages. */
    fun addNewMessageListener(listener: (UIMessage) -> Unit)

    /**
     * Retrieve the last n messages for the given contact starting backwards at the given index.
     *
     * Examples:
     *
     * getLastMessagesFor("a", 0, 100): Returns message numbers [0, 99]
     * getLastMessagesFor("a", 100, 100): Returns message numbers [100, 199]
     *
     * @param contactName Name of contact.
     * @param startingAt Backwards index to start at.
     * @param count Max number of messages to retrieve.
     *
     * @return Up to count messages
     */
    fun getLastMessagesFor(contactName: String, startingAt: Int, count: Int): Promise<List<UIMessage>, Exception>
}

