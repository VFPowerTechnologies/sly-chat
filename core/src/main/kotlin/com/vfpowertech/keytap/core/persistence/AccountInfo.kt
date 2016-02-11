package com.vfpowertech.keytap.core.persistence

import com.fasterxml.jackson.annotation.JsonProperty

data class AccountInfo(
    @JsonProperty("name")
    val name: String,

    @JsonProperty("email")
    val email: String,

    @param:JsonProperty("phone-number")
    @get:JsonProperty("phone-number")
    val phoneNumber: String
)
