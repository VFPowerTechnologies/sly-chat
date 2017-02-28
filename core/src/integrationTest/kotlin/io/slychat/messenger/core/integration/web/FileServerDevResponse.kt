package io.slychat.messenger.core.integration.web

import com.fasterxml.jackson.annotation.JsonProperty

data class FileServerDevResponse(
    @JsonProperty("storageEnabled")
    val storageEnabled: Boolean
)