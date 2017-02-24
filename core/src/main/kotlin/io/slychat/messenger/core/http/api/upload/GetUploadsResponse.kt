package io.slychat.messenger.core.http.api.upload

import com.fasterxml.jackson.annotation.JsonProperty

data class GetUploadsResponse(
    @JsonProperty("uploads")
    val uploads: List<UploadInfo>
)