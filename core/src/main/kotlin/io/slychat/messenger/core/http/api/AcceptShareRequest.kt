package io.slychat.messenger.core.http.api

import com.fasterxml.jackson.annotation.JsonProperty
import io.slychat.messenger.core.UserId

class AcceptShareRequest(
    @JsonProperty("from")
    val from: UserId,
    @JsonProperty("fileId")
    val fileId: String,
    @JsonProperty("shareKey")
    val shareKey: String,
    @JsonProperty("userMetadata")
    val userMetadata: ByteArray
)