package io.slychat.messenger.services.config

import com.fasterxml.jackson.annotation.JsonProperty

/** Represents a notification sound file. */
data class SoundFilePath(
    @JsonProperty("displayName")
    val displayName: String,
    @JsonProperty("uri")
    val uri: String
)