package io.slychat.messenger.services.files

data class DownloadTransferProgress(val transferedBytes: Long, val totalBytes: Long) {
    fun add(bytes: Long): DownloadTransferProgress {
        return copy(transferedBytes = transferedBytes + bytes)
    }
}