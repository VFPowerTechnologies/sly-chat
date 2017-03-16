package io.slychat.messenger.services.files

import io.slychat.messenger.core.Quota
import io.slychat.messenger.core.files.RemoteFile
import io.slychat.messenger.core.persistence.FileListMergeResults

data class StorageSyncResult(
    val remoteUpdatesPerformed: Int,
    val mergeResults: FileListMergeResults,
    val newListVersion: Long,
    val quota: Quota
)