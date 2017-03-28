package io.slychat.messenger.core.integration.utils

import com.fasterxml.jackson.annotation.JsonProperty
import io.slychat.messenger.core.UserId
import io.slychat.messenger.core.crypto.SerializedKeyVault
import io.slychat.messenger.core.crypto.hashes.HashParams

data class SiteUser(
    @JsonProperty("id")
    val id: UserId,

    @JsonProperty("email")
    val email: String,

    @JsonProperty("hashParams")
    val hashParams: HashParams,

    @JsonProperty("publicKey")
    val publicKey: String,

    @JsonProperty("name")
    val name: String,

    @JsonProperty("phoneNumber")
    val phoneNumber: String,

    @JsonProperty("keyVault")
    val keyVault: SerializedKeyVault
)
