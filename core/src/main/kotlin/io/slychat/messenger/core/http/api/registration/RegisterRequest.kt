package io.slychat.messenger.core.http.api.registration

import com.fasterxml.jackson.annotation.JsonProperty

data class RegisterRequest(
    @JsonProperty("email")
    val email: String,

    @JsonProperty("name")
    val name: String,

    @JsonProperty("phoneNumber")
    val phoneNumber: String,

    @JsonProperty("hash")
    val hash: String,

    @JsonProperty("hashParams")
    val hashParams: String,

    @JsonProperty("publicKey")
    val publicKey: String,

    @param:JsonProperty("keyVault")
    @get:JsonProperty("keyVault")
    val serializedKeyVault: String
)