package com.vfpowertech.keytap.ui.services

/**
 * Represents a sent or received message.
 *
 * @property contactName Name of contact.
 * @property timestamp Formatted timestamp for when message was received.
 * @property message Message body, including any formatting data.
 *
 * @constructor
 */
data class UIMessage(val contactName: String, val timestamp: String, val message: String)