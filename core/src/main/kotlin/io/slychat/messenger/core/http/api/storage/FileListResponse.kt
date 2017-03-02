package io.slychat.messenger.core.http.api.storage

import com.fasterxml.jackson.annotation.JsonProperty

data class FileListResponse(
    @JsonProperty("version")
    val version: Long,
    @JsonProperty("files")
    val files: List<FileInfo>
)