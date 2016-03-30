package com.vfpowertech.keytap.services.ui

import com.fasterxml.jackson.annotation.JsonProperty

data class UiUpdatePhoneInfo(
        @JsonProperty("email")
        val email: String,
        @JsonProperty("password")
        val password: String,
        @JsonProperty("phoneNumber")
        val phoneNumber: String
)