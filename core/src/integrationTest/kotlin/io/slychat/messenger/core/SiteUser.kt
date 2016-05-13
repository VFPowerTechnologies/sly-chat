package io.slychat.messenger.core

import com.fasterxml.jackson.annotation.JsonProperty
import io.slychat.messenger.core.crypto.SerializedCryptoParams
import io.slychat.messenger.core.crypto.SerializedKeyVault

data class SiteUser(
    @JsonProperty("id")
    val id: UserId,

    @JsonProperty("username")
    val username: String,

    @JsonProperty("hashParams")
    val hashParams: SerializedCryptoParams,

    @JsonProperty("publicKey")
    val publicKey: String,

    @JsonProperty("name")
    val name: String,

    @JsonProperty("phoneNumber")
    val phoneNumber: String,

    @JsonProperty("keyVault")
    val keyVault: SerializedKeyVault
)
