package io.slychat.messenger.core.http.api.gcm

import com.fasterxml.jackson.annotation.JsonIgnore
import com.fasterxml.jackson.annotation.JsonProperty

data class RegisterResponse(
    @JsonProperty("error-message")
    val errorMessage: String?
) {
    @JsonIgnore
    val isSuccess = errorMessage == null
}