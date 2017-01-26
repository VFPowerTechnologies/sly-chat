package io.slychat.messenger.core.http.api.accountreset

import com.fasterxml.jackson.annotation.JsonProperty

data class ResetAccountRequest(
    @param:JsonProperty("username")
    val username: String
)