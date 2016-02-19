package com.vfpowertech.keytap.core

import com.fasterxml.jackson.annotation.JsonProperty
import com.vfpowertech.keytap.core.crypto.SerializedCryptoParams
import com.vfpowertech.keytap.core.crypto.SerializedKeyVault

data class SiteUser(
    @JsonProperty("username")
    val username: String,

    @JsonProperty("passwordHash")
    val passwordHash: String,

    @JsonProperty("hashParams")
    val hashParams: SerializedCryptoParams,

    @JsonProperty("publicKey")
    val publicKey: String,

    @JsonProperty("metadata")
    val metadata: Map<String, String>,

    @JsonProperty("keyVault")
    val keyVault: SerializedKeyVault
)