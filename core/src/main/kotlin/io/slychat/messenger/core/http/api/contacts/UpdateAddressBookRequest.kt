package io.slychat.messenger.core.http.api.contacts

import io.slychat.messenger.core.persistence.RemoteAddressBookEntry

/**
 * @property entries List of new entries.
 */
data class UpdateAddressBookRequest(
    val entries: List<RemoteAddressBookEntry>
)