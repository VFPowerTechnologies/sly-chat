package io.slychat.messenger.core.http.api.pushnotifications

import com.fasterxml.jackson.annotation.JsonIgnore
import com.fasterxml.jackson.annotation.JsonProperty

data class RegisterResponse(
    @JsonProperty("unregistrationToken")
    val unregistrationToken: String,
    @JsonProperty("errorMessage")
    val errorMessage: String?
) {
    @JsonIgnore
    val isSuccess = errorMessage == null
}