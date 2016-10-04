package io.slychat.messenger.core.http.api.registration

import com.fasterxml.jackson.annotation.JsonIgnore
import com.fasterxml.jackson.annotation.JsonProperty

data class SmsVerificationResponse(
    @JsonProperty("errorMessage")
    val errorMessage: String?
) {
    @get:JsonIgnore
    val isSuccess: Boolean = errorMessage == null
}
