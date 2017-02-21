package io.slychat.messenger.core.http.api.upload

import com.fasterxml.jackson.annotation.JsonProperty
import io.slychat.messenger.core.Quota

data class NewUploadResponse(
    //true if had enough quota and upload was created
    @JsonProperty("hadSufficientQuota")
    val hadSufficientQuota: Boolean,
    @JsonProperty("quota")
    val quota: Quota
)