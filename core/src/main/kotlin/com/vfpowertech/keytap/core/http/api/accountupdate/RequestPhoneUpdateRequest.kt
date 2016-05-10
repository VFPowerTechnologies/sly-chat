package com.vfpowertech.keytap.core.http.api.accountupdate

import com.fasterxml.jackson.annotation.JsonProperty

data class RequestPhoneUpdateRequest(
    @param:JsonProperty("auth-token")
    @get:JsonProperty("auth-token")
    val authToken: String,

    @param:JsonProperty("phone-number")
    @get:JsonProperty("phone-number")
    val phoneNumber: String
)