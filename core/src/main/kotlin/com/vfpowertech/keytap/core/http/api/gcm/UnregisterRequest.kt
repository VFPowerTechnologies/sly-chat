package com.vfpowertech.keytap.core.http.api.gcm

import com.fasterxml.jackson.annotation.JsonProperty

data class UnregisterRequest(
    @get:JsonProperty("auth-token")
    val authToken: String,
    val installationId: String
)