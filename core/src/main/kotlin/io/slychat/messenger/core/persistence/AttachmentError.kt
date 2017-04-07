package io.slychat.messenger.core.persistence

enum class AttachmentError(val isTransient: Boolean) {
    NETWORK_ISSUE(true),
    SERVICE_UNAVAILABLE(true),
    INSUFFICIENT_QUOTA(false),
    //should never happen due to how the auto-naming works
    DUPLICATE_FILE(false),
    UNKNOWN(true)
}