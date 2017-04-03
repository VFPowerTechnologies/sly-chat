package io.slychat.messenger.core.http.api.share

import com.fasterxml.jackson.annotation.JsonProperty
import io.slychat.messenger.core.Quota

data class AcceptShareResponse(
    //ourFileId->failure
    @JsonProperty("errors")
    val errors: Map<String, AcceptShareError>,
    @JsonProperty("quota")
    val quota: Quota
)