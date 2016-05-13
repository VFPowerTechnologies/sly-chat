package io.slychat.messenger.core

import com.fasterxml.jackson.annotation.JsonProperty

data class SiteSignedPreKeyData(
    @JsonProperty("signedPreKey")
    val signedPreKey: String?
)

