package io.slychat.messenger.services.ui

enum class UIAttachmentCacheEventType {
    AVAILABLE
}

sealed class UIAttachmentCacheEvent {
    abstract val type: UIAttachmentCacheEventType

    class Available(val fileId: String, val resolution: Int) : UIAttachmentCacheEvent() {
        override val type: UIAttachmentCacheEventType
            get() = UIAttachmentCacheEventType.AVAILABLE
    }
}