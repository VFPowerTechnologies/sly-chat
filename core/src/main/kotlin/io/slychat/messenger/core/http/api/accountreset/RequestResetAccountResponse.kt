package io.slychat.messenger.core.http.api.accountreset

import com.fasterxml.jackson.annotation.JsonIgnore
import com.fasterxml.jackson.annotation.JsonProperty

data class RequestResetAccountResponse(
    @param:JsonProperty("errorMessage")
    val errorMessage: String?,

    @param:JsonProperty("emailIsReleased")
    val emailIsReleased: Boolean?,

    @param:JsonProperty("phoneNumberIsReleased")
    val phoneNumberIsReleased: Boolean?
) {
    @get:JsonIgnore
    val isSuccess: Boolean = errorMessage == null
}
