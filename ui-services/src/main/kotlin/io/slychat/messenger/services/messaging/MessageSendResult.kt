package io.slychat.messenger.services.messaging

import io.slychat.messenger.core.UserId
import io.slychat.messenger.core.relay.base.DeviceMismatchContent

/** Result of sending a message via the relay. */
interface MessageSendResult {
    val messageId: String
}

data class MessageSendOk(val to: UserId, override val messageId: String, val timestamp: Long) : MessageSendResult
data class MessageSendDeviceMismatch(val to: UserId, override val messageId: String, val info: DeviceMismatchContent) : MessageSendResult
//data class MessageSendUnknownFailure(val cause: Throwable) : MessageSendResult
