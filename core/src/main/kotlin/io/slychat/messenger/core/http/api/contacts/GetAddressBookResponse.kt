package io.slychat.messenger.core.http.api.contacts

import com.fasterxml.jackson.annotation.JsonProperty
import io.slychat.messenger.core.persistence.RemoteAddressBookEntry

data class GetAddressBookResponse(@JsonProperty("entries") val entries: List<RemoteAddressBookEntry>)