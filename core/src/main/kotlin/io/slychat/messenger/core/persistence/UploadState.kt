package io.slychat.messenger.core.persistence

enum class UploadState {
    //created locally but not on the remote server yet
    PENDING,
    //needs to be written to local cache (upload has been created remotely)
    CACHING,
    //remote upload entry has been created (and file has been cached locally if required)
    CREATED,
    //only complete once all parts are completed
    COMPLETE,
    //not cancelled on server yet
    CANCELLING,
    //cancelled
    CANCELLED
}