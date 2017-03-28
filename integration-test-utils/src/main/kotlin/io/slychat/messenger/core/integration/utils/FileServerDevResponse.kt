package io.slychat.messenger.core.integration.utils

import com.fasterxml.jackson.annotation.JsonProperty

data class FileServerDevResponse(
    @JsonProperty("storageEnabled")
    val storageEnabled: Boolean
)