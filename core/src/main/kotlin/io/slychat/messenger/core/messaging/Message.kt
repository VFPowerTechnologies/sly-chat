package io.slychat.messenger.core.messaging

import org.joda.time.DateTime

/**
 * @property contactName Name of contact
 * @property timestamp Time message was received
 * @constructor
 */
data class Message(val contactName: String, val timestamp: DateTime, val message: String)

