package io.slychat.messenger.core.http.api

import com.fasterxml.jackson.annotation.JsonProperty

data class UpdateMetadataResponse(
    @JsonProperty("newVersion")
    val newVersion: Int
)