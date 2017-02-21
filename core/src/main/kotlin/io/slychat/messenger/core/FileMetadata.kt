package io.slychat.messenger.core

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonProperty
import io.slychat.messenger.core.crypto.ciphers.CipherId

@JsonIgnoreProperties(ignoreUnknown = true)
data class FileMetadata(
    @JsonProperty("fileSize")
    val fileSize: Long,
    @JsonProperty("cipherId")
    val cipherId: CipherId,
    @JsonProperty("chunkSize")
    val chunkSize: Long
)