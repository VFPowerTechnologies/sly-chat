package io.slychat.messenger.services.contacts

/**
 * @property successful Whether or not the sync completed successfully.
 * @property updateCount The total number of AddressBookUpdates.
 * @property fullPull True if our local address book version was out of date with the remote version.
 */
data class AddressBookSyncResult(
    val successful: Boolean,
    val updateCount: Int,
    val fullPull: Boolean
)

