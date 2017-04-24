package io.slychat.messenger.services.files.cache

import io.slychat.messenger.services.messaging.AttachmentSource

sealed class AttachmentCacheEvent {
    class Added(val fileId: String) : AttachmentCacheEvent()
    class Failed(val attachmentSource: AttachmentSource) : AttachmentCacheEvent()
}