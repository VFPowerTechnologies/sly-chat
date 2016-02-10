package com.vfpowertech.keytap.core.http.api.registration

import com.fasterxml.jackson.annotation.JsonIgnore
import com.fasterxml.jackson.annotation.JsonProperty

data class RegisterResponse(
    @param:JsonProperty("error-message")
    @get:JsonProperty("error-message")
    val errorMessage: String?,

    @param:JsonProperty("validation-errors")
    @get:JsonProperty("validation-errors")
    val validationErrors: Map<String, List<String>>?
) {
    @get:JsonIgnore
    val isSuccess: Boolean = errorMessage == null
}
