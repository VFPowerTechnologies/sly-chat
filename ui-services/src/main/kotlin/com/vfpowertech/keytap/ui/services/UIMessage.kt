package com.vfpowertech.keytap.ui.services

/**
 * Represents a sent or received message.
 *
 * Do not rely on the message IDs being sequential, even for messages that follow each other,
 * as due to syncing these may be out of order.
 *
 * @property id Message ID.
 * @property isSent Whether or not this was a message we sent someone.
 * @property timestamp Formatted timestamp for when message was received. If null, is currently waiting to be sent.
 * @property message Message body, including any formatting data.
 *
 * @constructor
 */
data class UIMessage(
    val id: Int,
    val isSent: Boolean,
    val timestamp: String?,
    val message: String
)