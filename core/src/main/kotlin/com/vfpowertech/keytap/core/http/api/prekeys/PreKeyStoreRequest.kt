package com.vfpowertech.keytap.core.http.api.prekeys

import com.fasterxml.jackson.annotation.JsonProperty

/** All stores are in stored as hexified strings. */
data class PreKeyStoreRequest(
    @param:JsonProperty("auth-token")
    @get:JsonProperty("auth-token")
    val authToken: String,

    @param:JsonProperty("identity-key")
    @get:JsonProperty("identity-key")
    val identityKey: String,

    @param:JsonProperty("signed-prekey")
    @get:JsonProperty("signed-prekey")
    val signedPreKey: String,

    @param:JsonProperty("one-time-prekeys")
    @get:JsonProperty("one-time-prekeys")
    val oneTimePreKeys: List<String>,

    @param:JsonProperty("last-resort-prekey")
    @get:JsonProperty("last-resort-prekey")
    val lastResortPreKey: String
)