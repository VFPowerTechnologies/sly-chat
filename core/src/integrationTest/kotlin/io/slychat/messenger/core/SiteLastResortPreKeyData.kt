package io.slychat.messenger.core

import com.fasterxml.jackson.annotation.JsonProperty

data class SiteLastResortPreKeyData(
    @JsonProperty("lastResortPreKey")
    val lastResortPreKey: String?
)