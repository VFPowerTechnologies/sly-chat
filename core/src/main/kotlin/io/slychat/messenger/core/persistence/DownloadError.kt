package io.slychat.messenger.core.persistence

enum class DownloadError(override val isTransient: Boolean) : TransferError {
    //remote file was deleted
    REMOTE_FILE_MISSING(false),
    //ran out of disk space
    NO_SPACE(false),
    //download failed due to data corruption
    CORRUPTED(false),
    //things like disconnection, etc
    NETWORK_ISSUE(true),
    //503 from server
    SERVICE_UNAVAILABLE(true),
    //occurs if downloading a file encrypted with a cipher this version of Sly doesn't support
    UNKNOWN_CIPHER(false),
    //not really sure whether to mark this transient or not
    UNKNOWN(true)
}