package io.slychat.messenger.services.ui

import io.slychat.messenger.core.persistence.AttachmentId

enum class UIAttachmentCacheEventType {
    AVAILABLE,
    FILE_ID_UPDATE,
    INLINE_UPDATE
}

sealed class UIAttachmentEvent {
    abstract val type: UIAttachmentCacheEventType

    class Available(val fileId: String, val resolution: Int) : UIAttachmentEvent() {
        override val type: UIAttachmentCacheEventType
            get() = UIAttachmentCacheEventType.AVAILABLE
    }

    class FileIdUpdate(val updates: Map<AttachmentId, String>) : UIAttachmentEvent() {
        override val type: UIAttachmentCacheEventType
            get() = UIAttachmentCacheEventType.FILE_ID_UPDATE
    }


    class InlineUpdate(val updates: Map<AttachmentId, Boolean>) : UIAttachmentEvent() {
        override val type: UIAttachmentCacheEventType
            get() = UIAttachmentCacheEventType.INLINE_UPDATE
    }
}