package com.vfpowertech.keytap.core.http.api.registration

import com.fasterxml.jackson.annotation.JsonProperty

data class SmsResendRequest(
    @JsonProperty("username")
    val username: String
)