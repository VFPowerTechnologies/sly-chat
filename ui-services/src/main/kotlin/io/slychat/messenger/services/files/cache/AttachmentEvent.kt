package io.slychat.messenger.services.files.cache

import io.slychat.messenger.core.persistence.AttachmentId

sealed class AttachmentEvent {
    data class FileIdUpdate(val updates: Map<AttachmentId, String>) : AttachmentEvent()

    data class InlineUpdate(val updates: Map<AttachmentId, Boolean>) : AttachmentEvent()

    /**
     * If resolution is 0, then the original file is available.
     */
    data class AvailableInCache(val fileId: String, val resolution: Int) : AttachmentEvent()
}