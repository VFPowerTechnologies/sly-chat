package com.vfpowertech.keytap.core.http.api.offline

import com.fasterxml.jackson.annotation.JsonProperty

data class OfflineMessagesClearRequest(
    @get:JsonProperty("auth-token")
    val authToken: String,
    val range: String
)