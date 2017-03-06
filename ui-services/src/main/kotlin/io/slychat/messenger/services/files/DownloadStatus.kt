package io.slychat.messenger.services.files

import io.slychat.messenger.core.files.RemoteFile
import io.slychat.messenger.core.persistence.Download

data class DownloadStatus(
    val download: Download,
    val file: RemoteFile,
    val state: TransferState,
    val progress: DownloadTransferProgress
)