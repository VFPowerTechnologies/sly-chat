package com.vfpowertech.keytap.core.http.api.authentication

import com.fasterxml.jackson.annotation.JsonIgnore
import com.fasterxml.jackson.annotation.JsonProperty
import com.vfpowertech.keytap.core.AuthToken
import com.vfpowertech.keytap.core.crypto.SerializedKeyVault
import com.vfpowertech.keytap.core.persistence.AccountInfo

data class AuthenticationData(
    @param:JsonProperty("auth-token")
    @get:JsonProperty("auth-token")
    val authToken: AuthToken,

    @param:JsonProperty("key-vault")
    @get:JsonProperty("key-vault")
    val keyVault: SerializedKeyVault,

    @param:JsonProperty("account-info")
    @get:JsonProperty("account-info")
    val accountInfo: AccountInfo
)

data class AuthenticationResponse(
    @param:JsonProperty("error-message")
    @get:JsonProperty("error-message")
    val errorMessage: String?,

    @param:JsonProperty("data")
    @get:JsonProperty("data")
    val data: AuthenticationData?
) {
    @get:JsonIgnore
    val isSuccess: Boolean = errorMessage == null
}