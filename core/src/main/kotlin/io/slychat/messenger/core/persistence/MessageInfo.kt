package io.slychat.messenger.core.persistence

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
    init {
        if (!isSent) require(isDelivered) { "isDelivered must be true when isSent is false" }
    }
}