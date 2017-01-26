package io.slychat.messenger.core.http.api.accountreset

import com.fasterxml.jackson.annotation.JsonProperty

data class ResetConfirmCodeRequest(
    @param:JsonProperty("username")
    val username: String,

    @param:JsonProperty("verificationCode")
    val verificationCode: String
)