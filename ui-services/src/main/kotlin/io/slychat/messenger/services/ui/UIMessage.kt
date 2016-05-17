package io.slychat.messenger.services.ui

/**
 * Represents a sent or received message.
 *
 * Do not rely on the message IDs being sequential, even for messages that follow each other,
 * as due to syncing these may be out of order.
 *
 * @property id Message ID.
 * @property isSent Whether or not this was a message we sent someone.
 * @property timestamp Timestamp for when message was sent.
 * @property receivedTimestamp Timestamp for when message was received; if isSent is true, then this is when the server received the message, otherwise this is when the client received the message.
 * @property message Message body, including any formatting data.
 *
 * @constructor
 */
data class UIMessage(
    val id: String,
    val isSent: Boolean,
    val timestamp: Long,
    val receivedTimestamp: Long,
    val message: String
)