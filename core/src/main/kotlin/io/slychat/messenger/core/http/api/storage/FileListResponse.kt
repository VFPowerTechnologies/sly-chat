package io.slychat.messenger.core.http.api.storage

import com.fasterxml.jackson.annotation.JsonProperty
import io.slychat.messenger.core.Quota

data class FileListResponse(
    @JsonProperty("version")
    val version: Long,
    @JsonProperty("files")
    val files: List<FileInfo>,
    @JsonProperty("quota")
    val quota: Quota
)