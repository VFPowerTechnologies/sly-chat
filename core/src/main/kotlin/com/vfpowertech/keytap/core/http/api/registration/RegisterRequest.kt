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

    @param:JsonProperty("prikey-enc")
    @get:JsonProperty("prikey-enc")
    val encryptedPrivateKey: String,

    @param:JsonProperty("key-hash-params")
    @get:JsonProperty("key-hash-params")
    val keyHashParams: String,

    @param:JsonProperty("key-enc-params")
    @get:JsonProperty("key-enc-params")
    val keyEncryptionParams: String
)