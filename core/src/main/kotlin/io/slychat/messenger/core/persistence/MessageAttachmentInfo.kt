package io.slychat.messenger.core.persistence

data class MessageAttachmentInfo(
    val n: Int,
    //name as sent by the sender?
    val displayName: String,
    //may be invalid if file was deleted since
    val fileId: String,
    //whether or not to request from cache
    val isInline: Boolean
)