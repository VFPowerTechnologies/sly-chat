package io.slychat.messenger.core.integration.utils

import com.fasterxml.jackson.annotation.JsonProperty

data class SiteSignedPreKeyData(
    @JsonProperty("signedPreKey")
    val signedPreKey: String?
)

