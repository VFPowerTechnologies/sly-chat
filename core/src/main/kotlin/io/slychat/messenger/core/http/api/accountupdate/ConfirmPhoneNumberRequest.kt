package io.slychat.messenger.core.http.api.accountupdate

import com.fasterxml.jackson.annotation.JsonProperty

data class ConfirmPhoneNumberRequest(
    @param:JsonProperty("sms-code")
    @get:JsonProperty("sms-code")
    val smsCode: String
)