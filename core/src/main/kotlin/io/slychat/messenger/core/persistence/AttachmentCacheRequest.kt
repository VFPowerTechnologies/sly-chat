package io.slychat.messenger.core.persistence

data class AttachmentCacheRequest(
    val fileId: String,
    val downloadId: String?,
    val state: State
) {
    enum class State {
        PENDING,
        DOWNLOADING
    }

    init {
        if (state == State.DOWNLOADING && downloadId == null)
            error("Downloading state but no downloadId")
    }
}