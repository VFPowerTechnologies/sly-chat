package io.slychat.messenger.core.http.api.contacts

import com.fasterxml.jackson.annotation.JsonProperty

data class GetContactsResponse(@JsonProperty("contacts") val contacts: List<RemoteContactEntry>)