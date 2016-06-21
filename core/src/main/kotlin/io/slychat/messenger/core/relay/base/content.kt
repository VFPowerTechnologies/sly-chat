/** Relay message content-related functions. */
@file:JvmName("RelayContent")
package io.slychat.messenger.core.relay.base

import com.fasterxml.jackson.annotation.JsonFormat
import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.annotation.JsonPropertyOrder
import com.fasterxml.jackson.databind.ObjectMapper

@JsonFormat(shape = JsonFormat.Shape.ARRAY)
@JsonPropertyOrder("deviceId", "registrationId", "message")
data class MessageContent(val deviceId: Int, val registrationId: Int, val message: ByteArray)

data class SendMessageContent(val content: List<MessageContent>)

data class DeviceMismatchContent(
    @JsonProperty("stale")
    val stale: List<Int>,
    @JsonProperty("missing")
    val missing: List<Int>,
    @JsonProperty("removed")
    val removed: List<Int>
)

fun readDeviceMismatchContent(content: ByteArray): DeviceMismatchContent {
    val objectMapper = ObjectMapper()

    return objectMapper.readValue(content, DeviceMismatchContent::class.java)
}

fun writeSendMessageContent(content: SendMessageContent): ByteArray {
    val objectMapper = ObjectMapper()

    return objectMapper.writeValueAsBytes(content.content)
}