package com.vfpowertech.keytap.core.http.api.authentication

import com.fasterxml.jackson.annotation.JsonProperty

data class AuthenticationRequest(
    @param:JsonProperty("username")
    @get:JsonProperty("username")
    val username: String,

    @param:JsonProperty("hash")
    @get:JsonProperty("hash")
    val hash: String,

    @param:JsonProperty("csrf")
    @get:JsonProperty("csrf")
    val csrfToken: String,

    @JsonProperty("registrationId")
    val registrationId: Int,

    //set to 0 if no device is yet allocated
    @JsonProperty("deviceId")
    val deviceId: Int
)