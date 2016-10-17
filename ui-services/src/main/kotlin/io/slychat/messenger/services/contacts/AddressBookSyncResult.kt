package io.slychat.messenger.services.contacts

import io.slychat.messenger.core.persistence.ContactInfo

/**
 * @property successful Whether or not the sync completed successfully.
 * @property updateCount The total number of AddressBookUpdates.
 * @property addedLocalContacts A list of contacts which were added as a result of the platform contacts lookup.
 */
data class AddressBookSyncResult(
    val successful: Boolean = true,
    val updateCount: Int = 0,
    val pullResults: PullResults = PullResults(),
    val addedLocalContacts: List<ContactInfo> = emptyList()
)
