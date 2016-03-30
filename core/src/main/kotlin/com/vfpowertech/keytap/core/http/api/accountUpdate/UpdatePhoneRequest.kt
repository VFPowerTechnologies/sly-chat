package com.vfpowertech.keytap.core.http.api.accountUpdate

import com.fasterxml.jackson.annotation.JsonProperty

data class UpdatePhoneRequest(
    @param:JsonProperty("username")
    @get:com.fasterxml.jackson.annotation.JsonProperty("username")
    val username: String,

    @param:JsonProperty("hash")
    @get:com.fasterxml.jackson.annotation.JsonProperty("hash")
    val hash: String,

    @param:JsonProperty("phone-number")
    @get:com.fasterxml.jackson.annotation.JsonProperty("phone-number")
    val phoneNumber: String
)