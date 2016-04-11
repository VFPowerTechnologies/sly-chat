package com.vfpowertech.keytap.core.http.api.prekeys

import com.fasterxml.jackson.annotation.JsonProperty

data class SerializedPreKeySet(
    @param:JsonProperty("pubkey")
    @get:JsonProperty("pubkey")
    val publicKey: String,

    @param:JsonProperty("signed-prekey")
    @get:JsonProperty("signed-prekey")
    val signedPreKey: String,

    @param:JsonProperty("prekey")
    @get:JsonProperty("prekey")
    val preKey: String
)