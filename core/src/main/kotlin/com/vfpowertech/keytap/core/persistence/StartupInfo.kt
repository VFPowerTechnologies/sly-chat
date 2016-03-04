package com.vfpowertech.keytap.core.persistence

import com.fasterxml.jackson.annotation.JsonProperty

/** Optional info used by the app on startup. */
data class StartupInfo(
    @JsonProperty("lastLoggedInAccount")
    val lastLoggedInAccount: String,
    @JsonProperty("savedAccountPassword")
    val savedAccountPassword: String?
)