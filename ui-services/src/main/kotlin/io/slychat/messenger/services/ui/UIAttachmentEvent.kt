package io.slychat.messenger.services.ui

enum class UIAttachmentCacheEventType {
    AVAILABLE
}

sealed class UIAttachmentEvent {
    abstract val type: UIAttachmentCacheEventType

    class Available(val fileId: String, val resolution: Int) : UIAttachmentEvent() {
        override val type: UIAttachmentCacheEventType
            get() = UIAttachmentCacheEventType.AVAILABLE
    }
}