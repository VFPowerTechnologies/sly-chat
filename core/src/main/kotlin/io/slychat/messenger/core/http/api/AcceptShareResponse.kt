package io.slychat.messenger.core.http.api

import com.fasterxml.jackson.annotation.JsonProperty
import io.slychat.messenger.core.Quota

data class AcceptShareResponse(
    @JsonProperty("quota")
    val quota: Quota,
    //null if unsufficient quota
    @JsonProperty("fileId")
    val fileId: String?
)