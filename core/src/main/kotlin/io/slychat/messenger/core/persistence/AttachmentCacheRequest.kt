package io.slychat.messenger.core.persistence

data class AttachmentCacheRequest(
    val fileId: String,
    val downloadId: String?
)