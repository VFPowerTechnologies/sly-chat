package io.slychat.messenger.services.contacts

/** Info for a running job. */
data class AddressBookSyncJobInfo(
    val push: Boolean,
    val findLocalContacts: Boolean,
    val pull: Boolean
)