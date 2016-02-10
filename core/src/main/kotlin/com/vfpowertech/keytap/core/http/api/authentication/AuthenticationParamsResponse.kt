package com.vfpowertech.keytap.core.http.api.authentication

import com.fasterxml.jackson.annotation.JsonProperty
import com.vfpowertech.keytap.core.crypto.SerializedCryptoParams

data class AuthenticationParams(
    @param:JsonProperty("csrf")
    @get:JsonProperty("csrf")
    val csrfToken: String,

    @param:JsonProperty("hash-params")
    @get:JsonProperty("hash-params")
    val hashParams: SerializedCryptoParams
)

data class AuthenticationParamsResponse(
    @param:JsonProperty("successful")
    @get:JsonProperty("successful")
    val successful: Boolean,

    @param:JsonProperty("error-message")
    @get:JsonProperty("error-message")
    val errorMessage: String?,

    @param:JsonProperty("params")
    @get:JsonProperty("params")
    val params: AuthenticationParams?
)