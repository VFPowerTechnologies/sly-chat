package io.slychat.messenger.core.http.api.storage

import com.fasterxml.jackson.annotation.JsonProperty

data class UpdateResponse(
    @JsonProperty("newVersion")
    val newVersion: Long
)