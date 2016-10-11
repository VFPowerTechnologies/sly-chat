package io.slychat.messenger.core.http.api.gcm

import com.fasterxml.jackson.annotation.JsonProperty

data class IsRegisteredResponse(
    @JsonProperty("isRegistered")
    val isRegistered: Boolean
)