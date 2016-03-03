package com.vfpowertech.keytap.core.persistence

import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.annotation.JsonProperty

/** Optional info used by the app on startup. */
@JsonInclude(JsonInclude.Include.NON_NULL)
data class StartupInfo(
    @JsonProperty("lastLoggedInAccount")
    val lastLoggedInAccount: String,
    @JsonProperty("savedAccountPassword")
    val savedAccountPassword: String?
)