package com.vfpowertech.keytap.ui.services

import com.fasterxml.jackson.annotation.JsonProperty

data class UIContactInfo(
    @JsonProperty("id") val id: Int?,
    @JsonProperty("name") val name: String,
    @JsonProperty("phoneNumber") val phoneNumber: String?,
    @JsonProperty("email") val email: String
)