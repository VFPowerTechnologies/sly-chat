package io.slychat.messenger.core.http.api.authentication

import com.fasterxml.jackson.annotation.JsonProperty

data class AuthenticationRequest(
    @JsonProperty("username")
    val username: String,

    @JsonProperty("hash")
    val hash: String,

    @JsonProperty("csrf")
    val csrfToken: String,

    @JsonProperty("registrationId")
    val registrationId: Int,

    //set to 0 if no device is yet allocated
    @JsonProperty("deviceId")
    val deviceId: Int
)