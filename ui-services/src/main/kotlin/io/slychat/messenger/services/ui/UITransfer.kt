package io.slychat.messenger.services.ui

import io.slychat.messenger.core.persistence.*
import io.slychat.messenger.services.files.Transfer
import io.slychat.messenger.services.files.TransferState
import io.slychat.messenger.services.files.TransferStatus

enum class UITransferType {
    UPLOAD,
    DOWNLOAD
}

sealed class UITransfer {
    abstract val type: UITransferType

    class U(val upload: UIUpload) : UITransfer() {
        override val type: UITransferType
            get() = UITransferType.UPLOAD
    }

    class D(val download: UIDownload) : UITransfer() {
        override val type: UITransferType
            get() = UITransferType.DOWNLOAD
    }
}

class UIUpload(
    val id: String,
    val fileId: String?,
    val state: UploadState,
    val displayName: String,
    val remoteFilePath: String,
    val filePath: String,
    val error: UploadError?
)

fun Upload.toUI(): UIUpload {
    return UIUpload(
        id,
        fileId,
        state,
        displayName,
        remoteFilePath,
        filePath,
        error
    )
}

class UIDownload(
    val id: String,
    val fileId: String,
    val state: DownloadState,
    val filePath: String,
    val remoteFilePath: String,
    val error: DownloadError?
)

fun Download.toUI(): UIDownload {
    return UIDownload(id, fileId, state, filePath, remoteFilePath, error)
}

fun Transfer.toUI(): UITransfer {
    return when (this) {
        is Transfer.U -> UITransfer.U(upload.toUI())
        is Transfer.D -> UITransfer.D(download.toUI())
    }
}

class UITransferStatus(val transfer: UITransfer, val file: UIRemoteFile?, val state: TransferState, val progress: UITransferProgress)

fun TransferStatus.toUI(): UITransferStatus {
    return UITransferStatus(
        transfer.toUI(),
        file?.toUI(),
        state,
        progress.toUI()
    )
}
