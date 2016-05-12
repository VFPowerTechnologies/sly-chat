package com.vfpowertech.keytap.core

import com.fasterxml.jackson.annotation.JsonProperty
import com.vfpowertech.keytap.core.crypto.SerializedCryptoParams
import com.vfpowertech.keytap.core.crypto.SerializedKeyVault

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
