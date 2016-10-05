package io.slychat.messenger.core.http.api.accountupdate

import com.fasterxml.jackson.annotation.JsonProperty

data class UpdatePhoneRequest(
    @param:JsonProperty("email")
    val email: String,

    @param:JsonProperty("hash")
    val hash: String,

    @param:JsonProperty("phoneNumber")
    val phoneNumber: String
)