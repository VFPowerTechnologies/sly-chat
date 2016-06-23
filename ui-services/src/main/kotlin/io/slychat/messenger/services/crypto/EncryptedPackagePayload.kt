package io.slychat.messenger.services.crypto

import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.annotation.JsonSubTypes
import com.fasterxml.jackson.annotation.JsonTypeInfo
import com.fasterxml.jackson.databind.ObjectMapper

data class EncryptedPackagePayloadV0(
    @JsonProperty("preKeyWhisper")
    val isPreKeyWhisper: Boolean,
    @JsonProperty("payload")
    val payload: ByteArray
) : EncryptedPackagePayload

//TODO make sealed once data classes can be sealable
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.PROPERTY, property = "version")
@JsonSubTypes(
    JsonSubTypes.Type(EncryptedPackagePayloadV0::class, name = "0")
)
interface EncryptedPackagePayload

private fun upgradeEncryptedPackagePayload(encryptedPackagePayload: EncryptedPackagePayload): EncryptedPackagePayloadV0 {
    return when (encryptedPackagePayload) {
        is EncryptedPackagePayloadV0 -> encryptedPackagePayload
        else -> throw RuntimeException("Received unknown message version")
    }
}

/** Deserializes and possibly upgrades a received message wrapper. */
fun deserializeEncryptedPackagePayload(content: ByteArray): EncryptedPackagePayloadV0 {
    val encryptedMessage = ObjectMapper().readValue(content, EncryptedPackagePayload::class.java)

    return upgradeEncryptedPackagePayload(encryptedMessage)
}

fun deserializeEncryptedPackagePayload(content: String): EncryptedPackagePayloadV0 {
    val encryptedMessage = ObjectMapper().readValue(content, EncryptedPackagePayload::class.java)

    return upgradeEncryptedPackagePayload(encryptedMessage)
}

