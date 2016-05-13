package io.slychat.messenger.core.http.api.registration

import com.fasterxml.jackson.annotation.JsonProperty

data class SmsVerificationRequest(
    @JsonProperty("username")
    val username: String,

    @JsonProperty("code")
    val code: String
)