package com.vfpowertech.keytap.core.http.api.authentication

import com.fasterxml.jackson.annotation.JsonProperty

data class AuthenticationRequest(
    @param:JsonProperty("username")
    @get:com.fasterxml.jackson.annotation.JsonProperty("username")
    val username: String,

    @param:JsonProperty("hash")
    @get:com.fasterxml.jackson.annotation.JsonProperty("hash")
    val hash: String,

    @param:JsonProperty("csrf")
    @get:com.fasterxml.jackson.annotation.JsonProperty("csrf")
    val csrfToken: String
)