package io.slychat.messenger.services.files

import io.slychat.messenger.core.Quota
import io.slychat.messenger.core.files.RemoteFile

data class StorageSyncResult(
    val remoteUpdatesPerformed: Int,
    val updates: List<RemoteFile>,
    val newListVersion: Long,
    val quota: Quota
)