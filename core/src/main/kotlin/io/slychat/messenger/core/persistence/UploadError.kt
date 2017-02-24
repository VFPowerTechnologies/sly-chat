package io.slychat.messenger.core.persistence

//isTransient is true if the upload can be retried without user intervention
enum class UploadError(val isTransient: Boolean) {
    INSUFFICIENT_QUOTA(false),
}