package io.slychat.messenger.services.files

/** Transfer progress for a single part. */
data class UploadPartTransferProgress(
    val transferedBytes: Long,
    val totalBytes: Long
)