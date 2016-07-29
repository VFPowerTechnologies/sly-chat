package io.slychat.messenger.core.http.api.contacts

import io.slychat.messenger.core.persistence.RemoteAddressBookEntry

data class UpdateAddressBookRequest(
    val entries: List<RemoteAddressBookEntry>
)