package com.vfpowertech.keytap.core.persistence

import com.fasterxml.jackson.annotation.JsonProperty
import com.vfpowertech.keytap.core.UserId

/** Optional info used by the app on startup. */
data class StartupInfo(
    @JsonProperty("lastLoggedInAccount")
    val lastLoggedInAccount: UserId,
    @JsonProperty("savedAccountPassword")
    val savedAccountPassword: String
)