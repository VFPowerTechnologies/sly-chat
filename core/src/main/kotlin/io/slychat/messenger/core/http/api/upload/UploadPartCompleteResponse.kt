package io.slychat.messenger.core.http.api.upload

import com.fasterxml.jackson.annotation.JsonProperty

data class UploadPartCompleteResponse(
    @JsonProperty("checksum")
    val checksum: String
)