package io.slychat.messenger.core.http.api.registration

import com.fasterxml.jackson.annotation.JsonProperty

data class SmsResendRequest(
    @JsonProperty("email")
    val email: String
)