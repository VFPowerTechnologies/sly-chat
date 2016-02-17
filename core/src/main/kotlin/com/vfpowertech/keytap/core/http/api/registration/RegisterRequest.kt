package com.vfpowertech.keytap.core.http.api.registration

import com.fasterxml.jackson.annotation.JsonProperty

data class RegisterRequest(
    @JsonProperty("username")
    val username: String,

    @JsonProperty("metadata")
    val metadata: Map<String, String>,

    @JsonProperty("hash")
    val hash: String,

    @param:JsonProperty("hash-params")
    @get:JsonProperty("hash-params")
    val hashParams: String,

    @param:JsonProperty("pubkey")
    @get:JsonProperty("pubkey")
    val publicKey: String,

    @param:JsonProperty("key-vault")
    @get:JsonProperty("key-vault")
    val serializedKeyVault: String
)