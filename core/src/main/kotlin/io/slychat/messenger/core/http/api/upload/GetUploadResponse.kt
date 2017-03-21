package io.slychat.messenger.core.http.api.upload

import com.fasterxml.jackson.annotation.JsonProperty

class GetUploadResponse(
    @JsonProperty("upload")
    val upload: UploadInfo?
)