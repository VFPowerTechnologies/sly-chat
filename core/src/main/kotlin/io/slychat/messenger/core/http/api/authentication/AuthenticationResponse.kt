package io.slychat.messenger.core.http.api.authentication

import com.fasterxml.jackson.annotation.JsonIgnore
import com.fasterxml.jackson.annotation.JsonProperty
import io.slychat.messenger.core.AuthToken
import io.slychat.messenger.core.crypto.SerializedKeyVault
import io.slychat.messenger.core.persistence.AccountInfo

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