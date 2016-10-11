package io.slychat.messenger.core.http.api.authentication

import com.fasterxml.jackson.annotation.JsonProperty
import io.slychat.messenger.core.AuthToken

data class AuthenticationRefreshResponse(
    @JsonProperty("authToken")
    val authToken: AuthToken,
    @JsonProperty("expiresInMs")
    val expiresInMs: Long
)