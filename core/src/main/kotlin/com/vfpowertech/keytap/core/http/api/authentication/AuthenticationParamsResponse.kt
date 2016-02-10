package com.vfpowertech.keytap.core.http.api.authentication

import com.fasterxml.jackson.annotation.JsonProperty
import com.vfpowertech.keytap.core.crypto.SerializedCryptoParams

data class AuthenticationParamsResponse(
    @param:JsonProperty("csrf")
    @get:com.fasterxml.jackson.annotation.JsonProperty("csrf")
    val csrfToken: String,

    @param:JsonProperty("hash-params")
    @get:com.fasterxml.jackson.annotation.JsonProperty("hash-params")
    val hashParams: SerializedCryptoParams
)