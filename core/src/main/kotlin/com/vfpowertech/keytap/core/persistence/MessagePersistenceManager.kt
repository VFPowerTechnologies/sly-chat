package com.vfpowertech.keytap.core.persistence

import nl.komponents.kovenant.Promise

interface MessagePersistenceManager {
    /**
     * Appends a new message for the given user. Auto-generates a unique message id, along with a timestamp.
     *
     * @param contact If null, is a sent message.
     * @param message The message content itself.
     * @param ttl Unix time in seconds until when the message should be kept. If 0, is kept indefinitely, if < 0 is to be purged on startup.
     */
    fun addMessage(contact: String, isSent: Boolean, message: String, ttl: Long): Promise<MessageInfo, Exception>

    /** Stores the given list of received messages in the given order. There must not be any empty message lists. */
    fun addReceivedMessages(messages: Map<String, List<String>>): Promise<Map<String, List<MessageInfo>>, Exception>

    /** Marks a sent message as being received and updates its timestamp to the current time. */
    fun markMessageAsDelivered(contact: String, messageId: String): Promise<MessageInfo, Exception>

    /** Retrieve the last n messages for the given contact starting backwards at the given index. */
    fun getLastMessages(contact: String, startingAt: Int, count: Int): Promise<List<MessageInfo>, Exception>

    /** Returns all unsent messages. */
    fun getUndeliveredMessages(contact: String): Promise<List<MessageInfo>, Exception>
}