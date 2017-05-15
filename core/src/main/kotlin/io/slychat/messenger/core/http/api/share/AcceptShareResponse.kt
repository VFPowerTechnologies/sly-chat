package io.slychat.messenger.core.http.api.share

import com.fasterxml.jackson.annotation.JsonProperty
import io.slychat.messenger.core.Quota

data class AcceptShareResponse(
    //theirFileId->ourFileId (may differ from sent if already accepted on a diff device)
    @JsonProperty("successes")
    val successes: Map<String, String>,
    //theirFileId->failure
    @JsonProperty("errors")
    val errors: Map<String, AcceptShareError>,
    @JsonProperty("quota")
    val quota: Quota
)