package io.slychat.messenger.services.files

data class DownloadTransferProgress(override val transferedBytes: Long, override val totalBytes: Long) : TransferProgress {
    fun add(bytes: Long): DownloadTransferProgress {
        return copy(transferedBytes = transferedBytes + bytes)
    }
}