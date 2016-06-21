/** Relay message content-related functions. */
@file:JvmName("RelayContent")
package io.slychat.messenger.core.relay.base

import com.fasterxml.jackson.annotation.JsonFormat
import com.fasterxml.jackson.annotation.JsonPropertyOrder

@JsonFormat(shape = JsonFormat.Shape.ARRAY)
@JsonPropertyOrder("deviceId", "registrationId", "message")
data class MessageContent(val deviceId: Int, val registrationId: Int, val message: ByteArray)

@JsonFormat(shape = JsonFormat.Shape.ARRAY)
data class SendMessageContent(val content: List<MessageContent>)

data class DeviceMismatchContent(
    val stale: List<Int>,
    val missing: List<Int>,
    val removed: List<Int>
)