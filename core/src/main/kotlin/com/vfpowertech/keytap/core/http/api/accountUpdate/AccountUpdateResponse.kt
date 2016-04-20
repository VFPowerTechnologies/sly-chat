package com.vfpowertech.keytap.core.http.api.accountUpdate

import com.fasterxml.jackson.annotation.JsonIgnore
import com.fasterxml.jackson.annotation.JsonProperty

data class AccountInfo(
    @param:JsonProperty("id")
    @get:JsonProperty("id")
    val id: Long,

    @param:JsonProperty("name")
    @get:JsonProperty("name")
    val name: String,

    @param:JsonProperty("username")
    @get:JsonProperty("username")
    val username: String,

    @param:JsonProperty("phone-number")
    @get:JsonProperty("phone-number")
    val phoneNumber: String
)

data class AccountUpdateResponse(
    @param:JsonProperty("error-message")
    @get:JsonProperty("error-message")
    val errorMessage: String?,

    @param:JsonProperty("account-info")
    @get:JsonProperty("account-info")
    val accountInfo: AccountInfo?
) {
    @get:JsonIgnore
    val isSuccess: Boolean = errorMessage == null
}
