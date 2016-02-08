package com.vfpowertech.keytap.core.http.api.registration

import com.fasterxml.jackson.annotation.JsonProperty

data class RegisterResponse(
    @param:JsonProperty("registration-successful")
    @get:JsonProperty("registration-successful")
    val successful: Boolean,

    @param:JsonProperty("error-message")
    @get:JsonProperty("error-message")
    val errorMessage: String?
)
