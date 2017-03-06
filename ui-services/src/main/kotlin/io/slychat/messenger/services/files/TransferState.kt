package io.slychat.messenger.services.files

//state in transfer manager
enum class TransferState {
    QUEUED,
    //transferring data
    ACTIVE,
    //disabled by user
    CANCELLED,
    //complete
    COMPLETE,
    //error during some state (upload.error/download.error is set)
    ERROR
}