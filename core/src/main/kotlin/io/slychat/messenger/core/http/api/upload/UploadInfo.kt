package io.slychat.messenger.core.http.api.upload

import com.fasterxml.jackson.annotation.JsonProperty

data class UploadInfo(
    @JsonProperty("checksum")
    val uploadId: String
)