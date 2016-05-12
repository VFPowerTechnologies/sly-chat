package io.slychat.messenger.core.http.api.contacts

import com.fasterxml.jackson.annotation.JsonProperty
import io.slychat.messenger.core.PlatformContact

data class FindLocalContactsRequest(
    @JsonProperty("contacts")
    val contacts: List<PlatformContact>
)