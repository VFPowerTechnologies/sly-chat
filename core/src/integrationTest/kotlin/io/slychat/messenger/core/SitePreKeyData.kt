package io.slychat.messenger.core

import com.fasterxml.jackson.annotation.JsonProperty

data class SitePreKeyData(
    @JsonProperty("oneTimePreKeys")
    val oneTimePreKeys: List<String>,
    @JsonProperty("signedPreKey")
    val signedPreKey: String?,
    @JsonProperty("lastResortPreKey")
    val lastResortPreKey: String?
)