package io.slychat.messenger.core.persistence

//isTransient is true if the upload can be retried without user intervention
enum class UploadError(val isTransient: Boolean) {
    INSUFFICIENT_QUOTA(false),
    //filePath is invalid
    FILE_DISAPPEARED(false),
    //XXX this is only for single part uploads; we need to delete the old file and create a new upload in this case
    CORRUPTED(false),
    //things like disconnection, etc
    NETWORK_ISSUE(true)
}