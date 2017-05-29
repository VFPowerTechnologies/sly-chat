package io.slychat.messenger.services.files.cache

import io.slychat.messenger.core.persistence.AttachmentId

sealed class AttachmentEvent {
    /** A file ID correction for the given attachment. This occurs if another device has already accepted this attachment. */
    data class FileIdUpdate(val updates: Map<AttachmentId, String>) : AttachmentEvent()

    /**
     * Indicates an attachment's inline status has changed.
     *
     * Currently this will always indicate non-inline -> inline, but in the future user settings could be used to modify
     * inline parameters so this behavior should not be relied on.
     */
    data class InlineUpdate(val updates: Map<AttachmentId, Boolean>) : AttachmentEvent()

    /**
     * Indicates an attachment with the given resolution is now available in the cache. This can occur if the UI previously
     * requested it, or it was pre-cached from processing a message.
     *
     * If resolution is 0, then the original file is available.
     */
    data class AvailableInCache(val fileId: String, val resolution: Int) : AttachmentEvent()
}