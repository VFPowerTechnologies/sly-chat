package io.slychat.messenger.services.files.cache

sealed class AttachmentCacheEvent {
    /**
     * If resolution is 0, then the original file is available.
     */
    data class Available(val fileId: String, val resolution: Int) : AttachmentCacheEvent()
}