package com.vfpowertech.keytap.core.http.api.prekeys

import com.fasterxml.jackson.annotation.JsonProperty

data class PreKeyRetrieveResponse(
    @param:JsonProperty("for")
    @get:JsonProperty("for")
    val forUsername: String,

    @param:JsonProperty("identity-key")
    @get:JsonProperty("identity-key")
    val identityKey: String,

    @param:JsonProperty("signed-prekey")
    @get:JsonProperty("signed-prekey")
    val signedPreKey: String,

    @param:JsonProperty("prekey")
    @get:JsonProperty("prekey")
    val preKey: String
)