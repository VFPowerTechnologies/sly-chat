package io.slychat.messenger.core.persistence

import com.fasterxml.jackson.annotation.JsonProperty
import io.slychat.messenger.core.UserId

/** Optional info used by the app on startup. */
data class StartupInfo(
    @JsonProperty("lastLoggedInAccount")
    val lastLoggedInAccount: UserId,
    @JsonProperty("savedAccountPassword")
    val savedAccountPassword: String
)