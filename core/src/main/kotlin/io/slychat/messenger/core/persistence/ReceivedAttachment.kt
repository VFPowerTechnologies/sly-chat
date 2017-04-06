package io.slychat.messenger.core.persistence

import io.slychat.messenger.core.files.UserMetadata

data class ReceivedAttachment(
    val n: Int,
    val fileId: String,
    val theirShareKey: String,
    val userMetadata: UserMetadata,
    val isInline: Boolean,
    val downloadId: String?
) {
    init {
        if (downloadId != null && !isInline)
            error("downloadId set for non-inline attachment")

    }
}