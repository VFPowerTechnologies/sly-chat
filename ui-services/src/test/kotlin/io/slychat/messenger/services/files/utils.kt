package io.slychat.messenger.services.files

import io.slychat.messenger.core.randomDownload
import io.slychat.messenger.core.randomRemoteFile
import io.slychat.messenger.core.randomUpload

fun randomDownloadStatus(state: TransferState): DownloadStatus {
    val file = randomRemoteFile()
    val download = randomDownload(fileId = file.id)

    val transferedBytes = if (state == TransferState.COMPLETE)
        file.remoteFileSize
    else
        0L

    return DownloadStatus(
        download,
        file,
        state,
        DownloadTransferProgress(transferedBytes, file.remoteFileSize)
    )
}

fun randomDownloadTransferStatus(state: TransferState): TransferStatus {
    val status = randomDownloadStatus(state)
    return TransferStatus(Transfer.D(status.download), status.file, status.state, status.progress)
}

fun randomUploadStatus(state: TransferState): UploadStatus {
    val file = randomRemoteFile()
    //XXX state doesn't actually matter here
    val upload = randomUpload(fileId = file.id)

    return UploadStatus(
        upload,
        file,
        state,
        upload.parts.map {
            val transferedBytes = if (state == TransferState.COMPLETE)
                it.remoteSize
            else
                0L
            UploadPartTransferProgress(transferedBytes, it.remoteSize)
        }
    )
}

fun randomUploadTransferStatus(state: TransferState): TransferStatus {
    val status = randomUploadStatus(state)
    return TransferStatus(Transfer.U(status.upload), status.file, status.state, UploadTransferProgress(status.progress, status.transferedBytes, status.totalBytes))
}
