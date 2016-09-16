package io.slychat.messenger.core.persistence

import io.slychat.messenger.core.crypto.randomMessageId
import io.slychat.messenger.core.currentTimestamp

/**
 * Information about a conversation message.
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
    val isRead: Boolean,
    val isExpired: Boolean,
    val ttlMs: Long,
    val expiresAt: Long
) {
    companion object {
        fun newSent(message: String, ttlMs: Long): MessageInfo =
            MessageInfo(randomMessageId(), message, currentTimestamp(), 0, true, false, true, false, ttlMs, 0)

        fun newSelfSent(message: String, receivedTimestamp: Long, ttlMs: Long): MessageInfo {
            return MessageInfo(randomMessageId(), message, receivedTimestamp, receivedTimestamp, true, true, true, false, ttlMs, 0)
        }

        fun newSent(message: String, timestamp: Long, ttlMs: Long): MessageInfo =
            MessageInfo(randomMessageId(), message, timestamp, 0, true, false, true, false, ttlMs, 0)

        fun newReceived(id: String, message: String, timestamp: Long, receivedTimestamp: Long, isRead: Boolean, ttlMs: Long): MessageInfo =
            MessageInfo(id, message, timestamp, receivedTimestamp, false, true, isRead, false, ttlMs, 0)

        fun newReceived(message: String, timestamp: Long, ttlMs: Long): MessageInfo =
            MessageInfo(randomMessageId(), message, timestamp, currentTimestamp(), false, true, false, false, ttlMs, 0)


        fun newReceived(message: String, timestamp: Long, isRead: Boolean): MessageInfo =
            MessageInfo(randomMessageId(), message, timestamp, currentTimestamp(), false, true, isRead, false, 0, 0)
    }

    init {
        if (!isSent) require(isDelivered) { "isDelivered must be true when isSent is false" }
        if (isSent) require(isRead) { "isRead must be true when isSent is true" }

        require(ttlMs >= 0) { "ttlMs: $ttlMs < 0" }
        require(expiresAt >= 0) { "expiresAt: $expiresAt < 0" }
        require(timestamp >= 0) { "timestamp: $timestamp < 0" }
        require(receivedTimestamp >= 0) { "receivedTimestamp: $receivedTimestamp < 0" }
    }
}