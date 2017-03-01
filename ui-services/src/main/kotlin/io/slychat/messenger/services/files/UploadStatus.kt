package io.slychat.messenger.services.files

import io.slychat.messenger.core.files.RemoteFile
import io.slychat.messenger.core.persistence.Upload

//current state of transfer
//doesn't need to be persisted; can restore from normal upload state
data class UploadStatus(
    val upload: Upload,
    val file: RemoteFile,
    val state: UploadTransferState,
    val progress: List<UploadPartTransferProgress>
) {
    val transferedBytes: Long = progress.foldRight(0L) { p, t -> t + p.transferedBytes }
    val totalBytes: Long
        get() = file.remoteFileSize
}