package io.slychat.messenger.core.http.api.contacts

import com.fasterxml.jackson.annotation.JsonProperty
import io.slychat.messenger.core.persistence.ContactInfo

data class FindLocalContactsResponse(
    @JsonProperty("contacts")
    val contacts: List<ContactInfo>
)