package io.slychat.messenger.core.persistence

enum class DownloadError(val isTransient: Boolean) {
    //remote file was deleted
    REMOTE_FILE_MISSING(false),
    //ran out of disk space
    NO_SPACE(false),
    //download failed due to data corruption
    CORRUPTED(false),
    //things like disconnection, etc
    NETWORK_ISSUE(true),
    //user cancelled download
    CANCELLED(false),
    //not really sure whether to mark this transient or not
    UNKNOWN(true)
}