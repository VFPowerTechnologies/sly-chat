package com.vfpowertech.keytap.core.http.api.registration

import com.fasterxml.jackson.annotation.JsonProperty

data class RegisterResponse(
    @param:JsonProperty("successful")
    @get:JsonProperty("successful")
    val successful: Boolean,

    @param:JsonProperty("error-message")
    @get:JsonProperty("error-message")
    val errorMessage: String?,

    @param:JsonProperty("validation-errors")
    @get:JsonProperty("validation-errors")
    val validationErrors: Map<String, List<String>>?
)
