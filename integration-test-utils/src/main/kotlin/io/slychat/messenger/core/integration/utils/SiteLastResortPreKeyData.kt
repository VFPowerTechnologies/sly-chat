package io.slychat.messenger.core.integration.utils

import com.fasterxml.jackson.annotation.JsonProperty

data class SiteLastResortPreKeyData(
    @JsonProperty("lastResortPreKey")
    val lastResortPreKey: String?
)