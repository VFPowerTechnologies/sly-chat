package com.vfpowertech.keytap.core.http.api.authentication

import com.fasterxml.jackson.annotation.JsonIgnore
import com.fasterxml.jackson.annotation.JsonProperty

data class AuthenticationResponse(
    @param:JsonProperty("error-message")
    @get:JsonProperty("error-message")
    val errorMessage: String?,

    @param:JsonProperty("auth-token")
    @get:JsonProperty("auth-token")
    val authToken: String?,

    @param:JsonProperty("auth-upgrade")
    @get:JsonProperty("auth-upgrade")
    val authUpgrade: AuthUpgradeInfo?,

    @param:JsonProperty("key-regen")
    @get:JsonProperty("key-regen")
    val keyRegenCount: Int
) {
    @get:JsonIgnore
    val isSuccess: Boolean = errorMessage == null
}