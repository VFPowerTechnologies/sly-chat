package com.vfpowertech.keytap.core.http.api.accountupdate

import com.fasterxml.jackson.annotation.JsonProperty

data class UpdateEmailRequest(
    @param:JsonProperty("auth-token")
    @get:JsonProperty("auth-token")
    val authToken: String,

    @param:JsonProperty("email")
    @get:JsonProperty("email")
    val email: String
)