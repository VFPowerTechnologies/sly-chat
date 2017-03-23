package io.slychat.messenger.services.files

import io.slychat.messenger.core.files.RemoteFile
import io.slychat.messenger.core.persistence.Upload
import io.slychat.messenger.core.persistence.UploadState

//current state of transfer
//doesn't need to be persisted; can restore from normal upload state
data class UploadStatus(
    val upload: Upload,
    val file: RemoteFile?,
    val state: TransferState,
    val progress: List<UploadPartTransferProgress>
) {
    init {
        if (file == null) {
            if (upload.state != UploadState.CANCELLED)
                error("Only uploads in cancelled state may have file=null")
        }
    }

    val transferedBytes: Long = if (file != null) progress.foldRight(0L) { p, t -> t + p.transferedBytes } else -1
    val totalBytes: Long
        get() = file?.remoteFileSize ?: -1
}