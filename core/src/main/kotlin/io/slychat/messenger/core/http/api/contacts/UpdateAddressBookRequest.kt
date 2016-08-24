package io.slychat.messenger.core.http.api.contacts

import io.slychat.messenger.core.persistence.RemoteAddressBookEntry

/**
 * @property version Current client address book version.
 * @property entries List of new entries.
 */
data class UpdateAddressBookRequest(
    val version: Int,
    val entries: List<RemoteAddressBookEntry>
)