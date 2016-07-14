package io.slychat.messenger.services

/** Info for a running job. */
data class ContactSyncJobInfo(
    var updateRemote: Boolean,
    var localSync: Boolean,
    var remoteSync: Boolean,
    val isRunning: Boolean
)