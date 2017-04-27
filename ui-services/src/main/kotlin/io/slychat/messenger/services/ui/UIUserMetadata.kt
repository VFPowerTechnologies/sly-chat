package io.slychat.messenger.services.ui

import com.fasterxml.jackson.annotation.JsonProperty
import io.slychat.messenger.core.files.UserMetadata

class UIUserMetadata(
    @JsonProperty("directory")
    val directory: String,
    @JsonProperty("fileName")
    val fileName: String
)

fun UserMetadata.toUI(): UIUserMetadata {
    return UIUserMetadata(directory, fileName)
}