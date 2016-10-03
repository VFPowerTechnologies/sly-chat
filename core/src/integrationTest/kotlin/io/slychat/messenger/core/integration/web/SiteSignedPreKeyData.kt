package io.slychat.messenger.core.integration.web

import com.fasterxml.jackson.annotation.JsonProperty

data class SiteSignedPreKeyData(
    @JsonProperty("signedPreKey")
    val signedPreKey: String?
)

