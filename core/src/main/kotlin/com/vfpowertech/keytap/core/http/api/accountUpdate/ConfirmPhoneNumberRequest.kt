package com.vfpowertech.keytap.core.http.api.accountUpdate

import com.fasterxml.jackson.annotation.JsonProperty

data class ConfirmPhoneNumberRequest(
    @param:JsonProperty("auth-token")
    @get:JsonProperty("auth-token")
    val authToken: String,

    @param:JsonProperty("sms-code")
    @get:JsonProperty("sms-code")
    val smsCode: String
)