package io.slychat.messenger.core.http.api.upload

import com.fasterxml.jackson.annotation.JsonProperty

data class UploadCompleteResponse(
    @JsonProperty("fileId")
    val fileId: String
)