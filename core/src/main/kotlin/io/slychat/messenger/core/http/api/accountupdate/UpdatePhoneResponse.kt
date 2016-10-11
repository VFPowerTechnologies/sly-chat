package io.slychat.messenger.core.http.api.accountupdate

import com.fasterxml.jackson.annotation.JsonIgnore
import com.fasterxml.jackson.annotation.JsonProperty

data class UpdatePhoneResponse(
    @param:JsonProperty("errorMessage")
    val errorMessage: String?
) {
    @get:JsonIgnore
    val isSuccess: Boolean = errorMessage == null
}
