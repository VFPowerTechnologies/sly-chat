package io.slychat.messenger.services.crypto

import com.fasterxml.jackson.annotation.JsonFormat
import com.fasterxml.jackson.annotation.JsonPropertyOrder

/** Represents a single message to a user. */
@JsonFormat(shape = JsonFormat.Shape.ARRAY)
@JsonPropertyOrder("deviceId", "registrationId", "payload")
data class MessageData(
    val deviceId: Int,
    val registrationId: Int,
    val payload: EncryptedPackagePayloadV0
)