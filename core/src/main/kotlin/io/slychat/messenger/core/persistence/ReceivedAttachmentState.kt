package io.slychat.messenger.core.persistence

enum class ReceivedAttachmentState {
    //transitions either to WAITING_ON_SYNC if inline, or deleted (complete) if not inline
    PENDING,
    //waiting for file list sync for download info
    WAITING_ON_SYNC,
    //file was deleted by sender; this is terminal
    MISSING,
    //accepted(if not inline)/cached(if inline)
    COMPLETE
}