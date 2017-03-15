package io.slychat.messenger.core.persistence

enum class UploadState {
    //created locally but not on the remote server yet
    PENDING,
    //remote upload entry has been created
    CREATED,
    //needs to be written to local cache
    CACHING,
    //only complete once all parts are completed
    COMPLETE
}