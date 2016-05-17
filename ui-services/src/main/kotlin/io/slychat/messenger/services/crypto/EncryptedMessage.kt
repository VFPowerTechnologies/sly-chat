package io.slychat.messenger.services.crypto

import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.annotation.JsonSubTypes
import com.fasterxml.jackson.annotation.JsonTypeInfo
import com.fasterxml.jackson.databind.ObjectMapper

data class EncryptedMessageV0(
    @JsonProperty("preKeyWhisper")
    val isPreKeyWhisper: Boolean,
    @JsonProperty("payload")
    val payload: ByteArray
) : EncryptedMessage

//TODO make sealed once data classes can be sealable
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.PROPERTY, property = "version")
@JsonSubTypes(
    JsonSubTypes.Type(EncryptedMessageV0::class, name = "0")
)
interface EncryptedMessage

private fun upgradeEncryptedMessage(encryptedMessage: EncryptedMessage): EncryptedMessageV0 {
    return when (encryptedMessage) {
        is EncryptedMessageV0 -> encryptedMessage
        else -> throw RuntimeException("Received unknown message version")
    }
}

/** Deserializes and possibly upgrades a received message wrapper. */
fun deserializeEncryptedMessage(content: ByteArray): EncryptedMessageV0 {
    val encryptedMessage = ObjectMapper().readValue(content, EncryptedMessage::class.java)

    return upgradeEncryptedMessage(encryptedMessage)
}

fun deserializeEncryptedMessage(content: String): EncryptedMessageV0 {
    val encryptedMessage = ObjectMapper().readValue(content, EncryptedMessage::class.java)

    return upgradeEncryptedMessage(encryptedMessage)
}
