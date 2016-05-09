package com.vfpowertech.keytap.core.http.api.authentication

import com.fasterxml.jackson.annotation.JsonIgnore
import com.fasterxml.jackson.annotation.JsonProperty
import com.vfpowertech.keytap.core.crypto.SerializedKeyVaultV1
import com.vfpowertech.keytap.core.persistence.AccountInfo

data class AuthenticationData(
    @param:JsonProperty("auth-token")
    @get:JsonProperty("auth-token")
    val authToken: String,

    @param:JsonProperty("key-vault")
    @get:JsonProperty("key-vault")
    val keyVault: SerializedKeyVaultV1,

    @param:JsonProperty("account-info")
    @get:JsonProperty("account-info")
    val accountInfo: AccountInfo,

    @param:JsonProperty("auth-upgrade")
    @get:JsonProperty("auth-upgrade")
    val authUpgrade: AuthUpgradeInfo?
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