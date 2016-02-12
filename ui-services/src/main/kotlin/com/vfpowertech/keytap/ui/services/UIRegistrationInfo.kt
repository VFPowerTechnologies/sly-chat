package com.vfpowertech.keytap.ui.services

import com.fasterxml.jackson.annotation.JsonProperty

data class UIRegistrationInfo(
    @JsonProperty("name")
    val name: String,
    @JsonProperty("email")
    val email: String,
    @JsonProperty("password")
    val password: String,
    @JsonProperty("phoneNumber")
    val phoneNumber: String
)