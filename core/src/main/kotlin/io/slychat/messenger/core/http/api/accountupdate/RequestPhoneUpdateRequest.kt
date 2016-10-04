package io.slychat.messenger.core.http.api.accountupdate

import com.fasterxml.jackson.annotation.JsonProperty

data class RequestPhoneUpdateRequest(
    @param:JsonProperty("phoneNumber")
    val phoneNumber: String
)