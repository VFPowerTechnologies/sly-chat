package io.slychat.messenger.core.http.api.prekeys

import com.fasterxml.jackson.annotation.JsonProperty

data class SerializedPreKeySet(
    @JsonProperty("registrationId")
    val registrationId: Int,

    @JsonProperty("publicKey")
    val publicKey: String,

    @JsonProperty("signedPreKey")
    val signedPreKey: String,

    @JsonProperty("preKey")
    val preKey: String
)