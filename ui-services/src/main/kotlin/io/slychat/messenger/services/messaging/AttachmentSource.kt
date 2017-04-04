package io.slychat.messenger.services.messaging

/** Represents an attachment request from the UI. */
sealed class AttachmentSource {
    //this is used locally
    abstract val isInline: Boolean

    class Remote(val fileId: String, override val isInline: Boolean) : AttachmentSource()
}

