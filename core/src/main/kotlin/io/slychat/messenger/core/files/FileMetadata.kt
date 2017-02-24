package io.slychat.messenger.core.files

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonProperty
import io.slychat.messenger.core.crypto.ciphers.CipherId

@JsonIgnoreProperties(ignoreUnknown = true)
data class FileMetadata(
    @JsonProperty("size")
    val size: Long,
    @JsonProperty("cipherId")
    val cipherId: CipherId,
    @JsonProperty("chunkSize")
    val chunkSize: Int
)