package io.slychat.messenger.core.persistence

import io.slychat.messenger.core.files.UserMetadata

//TODO maybe the error should be attached to the attachment instead?
data class ReceivedAttachment(
    val conversationId: ConversationId,
    val messageId: String,
    val n: Int,
    val fileId: String,
    val theirShareKey: String,
    val userMetadata: UserMetadata,
    val isInline: Boolean,
    val downloadId: String?,
    val error: AttachmentError?
) {
    init {
        if (downloadId != null && !isInline)
            error("downloadId set for non-inline attachment")

    }
}
