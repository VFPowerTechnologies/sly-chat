package com.vfpowertech.keytap.core.http.api.prekeys

import com.fasterxml.jackson.annotation.JsonProperty

data class PreKeyInfoRequest(
    @get:JsonProperty("auth-token")
    val authToken: String
)