package io.slychat.messenger.services.contacts

/** Info for a running job. */
data class AddressBookSyncJobInfo(
    var updateRemote: Boolean,
    var localSync: Boolean,
    var remoteSync: Boolean
)