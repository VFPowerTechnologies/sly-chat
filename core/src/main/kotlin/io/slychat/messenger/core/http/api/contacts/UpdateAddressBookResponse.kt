package io.slychat.messenger.core.http.api.contacts

import com.fasterxml.jackson.annotation.JsonProperty

data class UpdateAddressBookResponse(
    @JsonProperty("version")
    val version: Int
)