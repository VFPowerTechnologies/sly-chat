package io.slychat.messenger.core.persistence

data class Download(
    val id: String,
    val fileId: String,
    val isComplete: Boolean,
    val filePath: String,
    val doDecrypt: Boolean,
    val error: DownloadError?
)