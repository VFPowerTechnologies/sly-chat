package io.slychat.messenger.core.persistence

import io.slychat.messenger.core.files.UserMetadata

//TODO maybe the error should be attached to the attachment instead?
data class ReceivedAttachment(
    val id: AttachmentId,
    val fileId: String,
    val theirShareKey: String,
    val userMetadata: UserMetadata,
    val state: ReceivedAttachmentState,
    val error: AttachmentError?
)
