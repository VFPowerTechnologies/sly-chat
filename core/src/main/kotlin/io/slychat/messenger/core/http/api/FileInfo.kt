package io.slychat.messenger.core.http.api

import com.fasterxml.jackson.annotation.JsonProperty

class FileInfo(
    @JsonProperty("id")
    val id: String,
    @JsonProperty("isDeleted")
    val isDeleted: Boolean,
    @JsonProperty("userMetadata")
    val userMetadata: ByteArray,
    @JsonProperty("creationDate")
    val creationDate: Long,
    @JsonProperty("modificationDate")
    val modificationDate: Long
)