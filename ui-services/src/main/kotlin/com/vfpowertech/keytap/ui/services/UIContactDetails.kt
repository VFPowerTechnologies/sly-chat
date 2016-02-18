package com.vfpowertech.keytap.ui.services

import com.fasterxml.jackson.annotation.JsonProperty

/**
 * Contact book details.
 */
data class UIContactDetails(
    @JsonProperty("id") val id: Int?,
    @JsonProperty("name") val name: String,
    @JsonProperty("phoneNumber") val phoneNumber: String?,
    @JsonProperty("email") val email: String,
    @JsonProperty("publicKey") val publicKey: String
)