package io.slychat.messenger.core.http.api.contacts

import com.fasterxml.jackson.annotation.JsonProperty

data class FindAllByIdResponse(
    @JsonProperty("contacts")
    val contacts: List<ApiContactInfo>
)
