package io.slychat.messenger.services.config

import com.fasterxml.jackson.annotation.JsonProperty

data class ConvoTTLSettings(
    @JsonProperty("enabled")
    val isEnabled: Boolean,
    @JsonProperty("lastTTL")
    val lastTTL: Long
)