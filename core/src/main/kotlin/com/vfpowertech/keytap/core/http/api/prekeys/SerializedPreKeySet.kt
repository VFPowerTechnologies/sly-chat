package com.vfpowertech.keytap.core.http.api.prekeys

import com.fasterxml.jackson.annotation.JsonProperty

data class SerializedPreKeySet(
    @param:JsonProperty("identity-key")
    @get:com.fasterxml.jackson.annotation.JsonProperty("identity-key")
    val identityKey: String,

    @param:JsonProperty("signed-prekey")
    @get:com.fasterxml.jackson.annotation.JsonProperty("signed-prekey")
    val signedPreKey: String,

    @param:JsonProperty("prekey")
    @get:com.fasterxml.jackson.annotation.JsonProperty("prekey")
    val preKey: String
)