package io.slychat.messenger.core.persistence

import io.slychat.messenger.core.currentTimestamp
import io.slychat.messenger.core.randomUUID

/**
 * Information about a conversation contact.
 *
 * @param id Message id. Is a 128bit UUID string.
 * @param timestamp Unix time, in milliseconds.
 * @param receivedTimestamp When isSent is true, this is when the server received the message. When isSent is false, this is when you received the message from the server. 0 when unset.
 * @param isDelivered When isSent is true, this indicates whether or not the message has been delivered to the relay server.
 */
data class MessageInfo(
    val id: String,
    val message: String,
    val timestamp: Long,
    val receivedTimestamp: Long,
    val isSent: Boolean,
    val isDelivered: Boolean,
    val ttl: Long
) {
    companion object {
        fun newSent(id: String, message: String, timestamp: Long, receivedTimestamp: Long, ttl: Long): MessageInfo =
            MessageInfo(id, message, timestamp, receivedTimestamp, true, false, ttl)

        fun newSent(message: String, ttl: Long): MessageInfo =
            MessageInfo(randomUUID(), message, currentTimestamp(), 0, true, false, ttl)

        fun newSelfSent(message: String, ttl: Long): MessageInfo {
            val receivedTimestamp = currentTimestamp()
            return MessageInfo(randomUUID(), message, receivedTimestamp, receivedTimestamp, true, true, ttl)
        }

        fun newSent(message: String, timestamp: Long, ttl: Long): MessageInfo =
            MessageInfo(randomUUID(), message, timestamp, 0, true, false, ttl)

        fun newSent(message: String, timestamp: Long, receivedTimestamp: Long, ttl: Long): MessageInfo =
            MessageInfo(randomUUID(), message, timestamp, receivedTimestamp, true, false, ttl)

        fun newReceived(id: String, message: String, timestamp: Long, receivedTimestamp: Long, ttl: Long): MessageInfo =
            MessageInfo(id, message, timestamp, receivedTimestamp, false, true, ttl)

        fun newReceived(message: String, timestamp: Long, receivedTimestamp: Long, ttl: Long): MessageInfo =
            MessageInfo(randomUUID(), message, timestamp, receivedTimestamp, false, true, ttl)

        fun newReceived(message: String, timestamp: Long, ttl: Long): MessageInfo =
            MessageInfo(randomUUID(), message, timestamp, currentTimestamp(), false, true, ttl)

        fun newReceived(message: String, timestamp: Long): MessageInfo =
            MessageInfo(randomUUID(), message, timestamp, currentTimestamp(), false, true, 0)
    }

    init {
        if (!isSent) require(isDelivered) { "isDelivered must be true when isSent is false" }
    }
}