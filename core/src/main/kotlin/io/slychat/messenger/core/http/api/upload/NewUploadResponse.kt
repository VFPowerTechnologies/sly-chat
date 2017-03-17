package io.slychat.messenger.core.http.api.upload

import com.fasterxml.jackson.annotation.JsonProperty
import io.slychat.messenger.core.Quota

data class NewUploadResponse(
    //null on successful allocation
    @JsonProperty("error")
    val error: NewUploadError?,
    @JsonProperty("quota")
    val quota: Quota
)