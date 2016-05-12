package io.slychat.messenger.core.http.api.registration

import com.fasterxml.jackson.annotation.JsonProperty

data class SmsResendRequest(
    @JsonProperty("username")
    val username: String
)