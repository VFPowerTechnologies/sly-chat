package io.slychat.messenger.core.http.api.accountupdate

import com.fasterxml.jackson.annotation.JsonIgnore
import com.fasterxml.jackson.annotation.JsonProperty

data class UpdatePhoneResponse(
    @param:JsonProperty("error-message")
    @get:com.fasterxml.jackson.annotation.JsonProperty("error-message")
    val errorMessage: String?
) {
    @get:JsonIgnore
    val isSuccess: Boolean = errorMessage == null
}
