package io.slychat.messenger.core.http.api.accountupdate

import com.fasterxml.jackson.annotation.JsonIgnore
import com.fasterxml.jackson.annotation.JsonProperty

data class AccountInfo(
    @param:JsonProperty("id")
    val id: Long,

    @param:JsonProperty("name")
    val name: String,

    @param:JsonProperty("email")
    val email: String,

    @param:JsonProperty("phoneNumber")
    val phoneNumber: String
)

data class AccountUpdateResponse(
    @param:JsonProperty("errorMessage")
    val errorMessage: String?,

    @param:JsonProperty("accountInfo")
    val accountInfo: AccountInfo?
) {
    @get:JsonIgnore
    val isSuccess: Boolean = errorMessage == null
}
