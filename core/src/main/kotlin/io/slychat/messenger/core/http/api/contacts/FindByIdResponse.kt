package io.slychat.messenger.core.http.api.contacts

import com.fasterxml.jackson.annotation.JsonProperty

data class FindByIdResponse(
    @JsonProperty("contactInfo")
    val contactInfo: ApiContactInfo?
)