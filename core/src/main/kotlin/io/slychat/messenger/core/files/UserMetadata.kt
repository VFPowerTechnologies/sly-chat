package io.slychat.messenger.core.files

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonProperty
import io.slychat.messenger.core.crypto.ciphers.Key

/**
 * @property fileKey Key used to encrypt file data.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
data class UserMetadata(
    @JsonProperty("fileKey")
    val fileKey: Key,
    //dir/filenames should be case-insensitive during comparisions
    @JsonProperty("directory")
    val directory: String,
    @JsonProperty("fileName")
    val fileName: String
) {
    init {
        require(fileName.isNotBlank()) { "Invalid file name: <<$fileName>>" }
        require(directory.startsWith("/")) { "Invalid directory path: <<$directory>>" }
    }
}