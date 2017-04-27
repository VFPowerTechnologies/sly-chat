package io.slychat.messenger.services.ui

import io.slychat.messenger.core.Quota
import io.slychat.messenger.core.persistence.FileListMergeResults
import io.slychat.messenger.services.files.FileListSyncEvent
import io.slychat.messenger.services.files.FileListSyncResult

enum class UIFileSyncEventType {
    BEGIN,
    RESULT,
    END
}

class UIFileListMergeResults(
    val added: List<UIRemoteFile>,
    val deleted: List<UIRemoteFile>,
    val updated: List<UIRemoteFile>
)

fun FileListMergeResults.toUI(): UIFileListMergeResults {
    return UIFileListMergeResults(
        added.map { it.toUI() },
        deleted.map { it.toUI() },
        updated.map { it.toUI() }
    )
}

class UIFileListSyncResult(
    val remoteUpdatesPerformed: Int,
    val mergeResults: UIFileListMergeResults,
    val newListVersion: Long,
    val quota: Quota
)

fun FileListSyncResult.toUI(): UIFileListSyncResult {
    return UIFileListSyncResult(remoteUpdatesPerformed, mergeResults.toUI(), newListVersion, quota)
}

sealed class UIFileSyncEvent {
    abstract val type: UIFileSyncEventType

    class Begin: UIFileSyncEvent() {
        override val type: UIFileSyncEventType
            get() = UIFileSyncEventType.BEGIN
    }

    class Result(val result: UIFileListSyncResult) : UIFileSyncEvent() {
        override val type: UIFileSyncEventType
            get() = UIFileSyncEventType.RESULT
    }

    class End(val hasError: Boolean) : UIFileSyncEvent() {
        override val type: UIFileSyncEventType
            get() = UIFileSyncEventType.END
    }
}

fun FileListSyncEvent.toUI(): UIFileSyncEvent {
    return when (this) {
        is FileListSyncEvent.Begin -> UIFileSyncEvent.Begin()
        is FileListSyncEvent.Result -> UIFileSyncEvent.Result(result.toUI())
        is FileListSyncEvent.End -> UIFileSyncEvent.End(hasError)
    }
}