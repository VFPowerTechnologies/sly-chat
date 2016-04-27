package com.vfpowertech.keytap.core.http.api.prekeys

import com.fasterxml.jackson.annotation.JsonProperty

/** All stores are in stored as hexified strings. */
data class PreKeyStoreRequest(
    @param:JsonProperty("auth-token")
    @get:JsonProperty("auth-token")
    val authToken: String,

    @JsonProperty("identityKey")
    val identityKey: String,

    @JsonProperty("signedPreKey")
    val signedPreKey: String,

    @param:JsonProperty("oneTimePreKeys")
    val oneTimePreKeys: List<String>,

    @JsonProperty("lastResortPreKey")
    val lastResortPreKey: String
)