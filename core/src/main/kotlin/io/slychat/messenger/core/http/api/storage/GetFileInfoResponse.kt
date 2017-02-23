package io.slychat.messenger.core.http.api.storage

import com.fasterxml.jackson.annotation.JsonProperty

class GetFileInfoResponse(
    @JsonProperty("fileInfo")
    val fileInfo: FileInfo
)