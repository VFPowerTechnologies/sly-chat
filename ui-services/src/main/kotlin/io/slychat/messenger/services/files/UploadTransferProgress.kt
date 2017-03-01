package io.slychat.messenger.services.files

data class UploadTransferProgress(
    val progress: List<UploadPartTransferProgress>,
    val transferedBytes: Long,
    val totalBytes: Long
)