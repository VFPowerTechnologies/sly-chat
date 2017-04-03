package io.slychat.messenger.core.http.api.share

import com.fasterxml.jackson.annotation.JsonProperty
import io.slychat.messenger.core.UserId

class AcceptShareRequest(
    @JsonProperty("theirUserId")
    val theirUserId: UserId,
    @JsonProperty("shareInfo")
    val shareInfo: List<ShareInfo>
)