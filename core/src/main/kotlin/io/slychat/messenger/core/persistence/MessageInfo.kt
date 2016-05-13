package io.slychat.messenger.core.persistence

/**
 * Information about a conversation contact.
 *
 * @param id Message id. Is a 128bit UUID string.
 * @param timestamp Unix time, in milliseconds.
 * @param isDelivered When isSent is true, this indicates whether or not the message has been delivered to the relay server.
 */
data class MessageInfo(
    val id: String,
    val message: String,
    val timestamp: Long,
    val isSent: Boolean,
    val isDelivered: Boolean,
    val ttl: Long
) {
    init {
        if (!isSent) require(isDelivered) { "isDelivered must be true when isSent is false" }
    }
}