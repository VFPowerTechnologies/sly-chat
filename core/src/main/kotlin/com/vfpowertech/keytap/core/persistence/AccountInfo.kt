package com.vfpowertech.keytap.core.persistence

import com.fasterxml.jackson.annotation.JsonProperty
import com.vfpowertech.keytap.core.UserId

data class AccountInfo(
    @JsonProperty("id")
    val id: UserId,

    @JsonProperty("name")
    val name: String,

    @JsonProperty("email")
    val email: String,

    @param:JsonProperty("phone-number")
    @get:JsonProperty("phone-number")
    val phoneNumber: String,

    @JsonProperty("deviceId")
    val deviceId: Int
)
