package io.slychat.messenger.core.http.api.upload

enum class NewUploadError {
    DUPLICATE_FILE,
    MAX_UPLOADS_EXCEEDED,
    INSUFFICIENT_QUOTA
}