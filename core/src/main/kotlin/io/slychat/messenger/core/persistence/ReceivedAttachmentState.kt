package io.slychat.messenger.core.persistence

enum class ReceivedAttachmentState {
    //accepted(if not inline)/cached(if inline) will delete the received attachment record, which acts as the COMPLETE state

    //transitions either to WAITING_ON_SYNC if inline, or deleted (complete) if not inline
    PENDING,
    //waiting for file list sync for download info
    WAITING_ON_SYNC,
    //file was deleted by sender; this is terminal
    MISSING
}