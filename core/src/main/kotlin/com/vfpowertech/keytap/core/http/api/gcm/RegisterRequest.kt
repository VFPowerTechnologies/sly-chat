package com.vfpowertech.keytap.core.http.api.gcm

import com.fasterxml.jackson.annotation.JsonProperty

data class RegisterRequest(
    @get:JsonProperty("auth-token")
    val authToken: String,
    val token: String,
    val installationId: String
)