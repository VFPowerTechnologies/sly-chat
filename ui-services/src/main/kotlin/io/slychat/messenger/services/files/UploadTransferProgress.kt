package io.slychat.messenger.services.files

data class UploadTransferProgress(
    val progress: List<UploadPartTransferProgress>,
    override val transferedBytes: Long,
    override val totalBytes: Long
) : TransferProgress