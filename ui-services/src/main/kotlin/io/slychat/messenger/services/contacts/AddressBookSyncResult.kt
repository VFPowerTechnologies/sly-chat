package io.slychat.messenger.services.contacts

import io.slychat.messenger.core.persistence.ContactInfo

/**
 * @property successful Whether or not the sync completed successfully.
 * @property updateCount The total number of AddressBookUpdates.
 * @property fullPull True if our local address book version was out of date with the remote version.
 * @property addedLocalContacts A list of contacts which were added as a result of the platform contacts lookup.
 */
data class AddressBookSyncResult(
    val successful: Boolean = true,
    val updateCount: Int = 0,
    val fullPull: Boolean = false,
    val addedLocalContacts: Set<ContactInfo> = emptySet()
)

