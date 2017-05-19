package io.slychat.messenger.core.http.api.share

import com.fasterxml.jackson.annotation.JsonProperty

data class ShareInfo(
    @JsonProperty("theirFileId")
    val theirFileId: String,
    @JsonProperty("ourFileId")
    val ourFileId: String,
    @JsonProperty("theirShareKey")
    val theirShareKey: String,
    @JsonProperty("ourShareKey")
    val ourShareKey: String,
    @JsonProperty("userMetadata")
    val userMetadata: ByteArray,
    @JsonProperty("pathHash")
    val pathHash: String
)