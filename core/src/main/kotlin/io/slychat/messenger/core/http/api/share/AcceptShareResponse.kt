package io.slychat.messenger.core.http.api.share

import com.fasterxml.jackson.annotation.JsonProperty
import io.slychat.messenger.core.Quota

data class AcceptShareResponse(
    //null on successful accept
    @JsonProperty("error")
    val error: AcceptShareError?,
    @JsonProperty("quota")
    val quota: Quota
)