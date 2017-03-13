package io.slychat.messenger.core.persistence

data class Download(
    val id: String,
    val fileId: String,
    val state: DownloadState,
    val filePath: String,
    val doDecrypt: Boolean,
    val error: DownloadError?
) {
    val isComplete: Boolean
        get() = state == DownloadState.COMPLETE
}