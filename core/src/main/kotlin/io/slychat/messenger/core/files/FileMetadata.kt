package io.slychat.messenger.core.files

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonProperty

@JsonIgnoreProperties(ignoreUnknown = true)
data class FileMetadata(
    @JsonProperty("size")
    val size: Long,
    @JsonProperty("chunkSize")
    val chunkSize: Int,
    @JsonProperty("mimeType")
    val mimeType: String
) {
    init {
        //just some minor checking
        val parts = mimeType.split('/')
        require(parts.size == 2) { "Invalid mimeType: $mimeType" }
    }
}