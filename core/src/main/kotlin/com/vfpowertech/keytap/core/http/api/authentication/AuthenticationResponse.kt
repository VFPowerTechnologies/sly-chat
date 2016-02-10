package com.vfpowertech.keytap.core.http.api.authentication

import com.fasterxml.jackson.annotation.JsonProperty

data class AuthenticationResponse(
    @param:JsonProperty("successful")
    @get:com.fasterxml.jackson.annotation.JsonProperty("successful")
    val successful: Boolean,

    @param:JsonProperty("error-message")
    @get:com.fasterxml.jackson.annotation.JsonProperty("error-message")
    val errorMessage: String?,

    @param:JsonProperty("auth-token")
    @get:com.fasterxml.jackson.annotation.JsonProperty("auth-token")
    val authToken: String?,

    @param:JsonProperty("auth-upgrade")
    @get:com.fasterxml.jackson.annotation.JsonProperty("auth-upgrade")
    val authUpgrade: AuthUpgradeInfo?,

    @param:JsonProperty("key-regen")
    @get:com.fasterxml.jackson.annotation.JsonProperty("key-regen")
    val keyRegen: Int?
)