package io.slychat.messenger.core.http.api

import com.fasterxml.jackson.annotation.JsonProperty

data class FileListResponse(
    @JsonProperty("latestVersion")
    val latestVersion: Int,
    @JsonProperty("files")
    val files: List<FileInfo>
)