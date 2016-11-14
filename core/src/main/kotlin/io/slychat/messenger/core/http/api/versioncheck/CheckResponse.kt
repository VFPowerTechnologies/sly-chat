package io.slychat.messenger.core.http.api.versioncheck

import com.fasterxml.jackson.annotation.JsonProperty

data class CheckResponse(
    @JsonProperty("isLatest")
    val isLatest: Boolean,
    @JsonProperty("latestVersion")
    val latestVersion: String
)