package io.slychat.messenger.core.persistence

enum class UploadState {
    //created locally but not on the remote server yet
    PENDING,
    //remote upload entry has been created
    CREATED,
    //this is either actually transfering, or queued to be transfering
    RUNNING,
    //only complete once all parts are completed
    COMPLETE,
    //when in this state, .error is set
    ERROR
}