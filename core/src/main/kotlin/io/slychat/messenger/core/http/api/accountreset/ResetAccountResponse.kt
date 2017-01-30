package io.slychat.messenger.core.http.api.accountreset

import com.fasterxml.jackson.annotation.JsonIgnore
import com.fasterxml.jackson.annotation.JsonProperty

data class ResetAccountResponse(
    @param:JsonProperty("errorMessage")
    val errorMessage: String?

) {
    @get:JsonIgnore
    val isSuccess: Boolean = errorMessage == null
}
