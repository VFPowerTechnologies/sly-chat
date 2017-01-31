package io.slychat.messenger.services

import com.fasterxml.jackson.annotation.JsonProperty

data class DeviceTokens(
    @JsonProperty("token")
    val token: String,
    @JsonProperty("audioToken")
    val audioToken: String?
)