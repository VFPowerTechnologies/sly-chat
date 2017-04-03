package io.slychat.messenger.core.http.api.share

import com.fasterxml.jackson.annotation.JsonProperty
import io.slychat.messenger.core.UserId

class AcceptShareRequest(
    @JsonProperty("fromUserId")
    val fromUserId: UserId,
    @JsonProperty("theirFileId")
    val theirFileId: String,
    @JsonProperty("theirShareKey")
    val theirShareKey: String,
    @JsonProperty("ourFileId")
    val ourFileId: String,
    @JsonProperty("ourShareKey")
    val ourShareKey: String,
    @JsonProperty("userMetadata")
    val userMetadata: ByteArray,
    @JsonProperty("pathHash")
    val pathHash: String
)