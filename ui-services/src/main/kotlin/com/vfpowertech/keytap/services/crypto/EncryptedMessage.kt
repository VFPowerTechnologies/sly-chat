package com.vfpowertech.keytap.services.crypto

import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.annotation.JsonSubTypes
import com.fasterxml.jackson.annotation.JsonTypeInfo

data class EncryptedMessageV0(
    @JsonProperty("preKeyWhisper")
    val isPreKeyWhisper: Boolean,
    @JsonProperty("payload")
    val payload: String
) : EncryptedMessage

//TODO make sealed once data classes can be sealable
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.PROPERTY, property = "version")
@JsonSubTypes(
    JsonSubTypes.Type(EncryptedMessageV0::class, name = "0")
)
interface EncryptedMessage