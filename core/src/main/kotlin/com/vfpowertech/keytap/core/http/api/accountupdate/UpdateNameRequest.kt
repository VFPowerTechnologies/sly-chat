package com.vfpowertech.keytap.core.http.api.accountupdate

import com.fasterxml.jackson.annotation.JsonProperty

data class UpdateNameRequest(
    @param:JsonProperty("auth-token")
    @get:JsonProperty("auth-token")
    val authToken: String,

    @param:JsonProperty("name")
    @get:JsonProperty("name")
    val name: String
)