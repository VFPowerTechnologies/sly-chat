package io.slychat.messenger.core.http.api.contacts

import com.fasterxml.jackson.annotation.JsonProperty

data class FindContactRequest(
    @JsonProperty("email")
    val email: String?,

    @JsonProperty("phoneNumber")
    val phoneNumber: String?
)