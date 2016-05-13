package io.slychat.messenger.core.http.api.contacts

import com.fasterxml.jackson.annotation.JsonProperty

data class AddContactsRequest(
    val contacts: List<RemoteContactEntry>
)