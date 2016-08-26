package io.slychat.messenger.services

import io.slychat.messenger.core.UserId

/** Represents a possible security-related error. */
open class SecurityException(message: String, cause: Throwable? = null) : Exception(message, cause)

class SyncMessageFromOtherSecurityException(
    val senderId: UserId,
    val messageType: String
) : SecurityException("Received a sync message of type $messageType from $senderId")
