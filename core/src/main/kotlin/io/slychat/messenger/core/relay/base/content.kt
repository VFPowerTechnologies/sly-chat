/** Relay message content-related functions. */
@file:JvmName("RelayContent")
package io.slychat.messenger.core.relay.base

import com.fasterxml.jackson.annotation.JsonFormat
import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.annotation.JsonPropertyOrder
import com.fasterxml.jackson.databind.ObjectMapper
import io.slychat.messenger.core.relay.RelayMessageBundle

@JsonFormat(shape = JsonFormat.Shape.ARRAY)
@JsonPropertyOrder("deviceId", "registrationId", "message")
data class MessageContent(val deviceId: Int, val registrationId: Int, val message: String)

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

fun writeSendMessageContent(messageBundle: RelayMessageBundle): ByteArray {
    val objectMapper = ObjectMapper()

    val messages = messageBundle.messages.map {
        val payload = objectMapper.writeValueAsString(it.message)
        MessageContent(it.deviceId, it.registrationId, payload)
    }

    return objectMapper.writeValueAsBytes(messages)
}