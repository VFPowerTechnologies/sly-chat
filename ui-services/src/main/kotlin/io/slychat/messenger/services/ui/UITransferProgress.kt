package io.slychat.messenger.services.ui

import io.slychat.messenger.services.files.TransferProgress

class UITransferProgress(
    val transferedBytes: Long,
    val totalBytes: Long
)

fun TransferProgress.toUI(): UITransferProgress {
    return UITransferProgress(transferedBytes, totalBytes)
}

