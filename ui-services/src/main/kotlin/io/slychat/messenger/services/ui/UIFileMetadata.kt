package io.slychat.messenger.services.ui

import com.fasterxml.jackson.annotation.JsonProperty
import io.slychat.messenger.core.files.FileMetadata

class UIFileMetadata(
    @JsonProperty("size")
    val size: Long,
    @JsonProperty("mimeType")
    val mimeType: String
)

fun FileMetadata.toUI(): UIFileMetadata {
    return UIFileMetadata(size, mimeType)
}