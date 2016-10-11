package io.slychat.messenger.core.http.api.registration

import com.fasterxml.jackson.annotation.JsonIgnore
import com.fasterxml.jackson.annotation.JsonProperty

data class RegisterResponse(
    @JsonProperty("errorMessage")
    val errorMessage: String?,

    @JsonProperty("validationErrors")
    val validationErrors: Map<String, List<String>>?
) {
    @get:JsonIgnore
    val isSuccess: Boolean = errorMessage == null
}
