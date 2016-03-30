package com.vfpowertech.keytap.services.ui

import com.fasterxml.jackson.annotation.JsonProperty

data class UIUpdatePhoneInfo(
        @JsonProperty("email")
        val email: String,
        @JsonProperty("password")
        val password: String,
        @JsonProperty("phoneNumber")
        val phoneNumber: String
)