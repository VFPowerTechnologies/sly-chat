package io.slychat.messenger.core.http.api.upload

import com.fasterxml.jackson.annotation.JsonProperty

data class UploadPartInfo(
    @JsonProperty("n")
    val n: Int,
    @JsonProperty("size")
    val size: Long,
    @JsonProperty("isComplete")
    val isComplete: Boolean
)