package io.slychat.messenger.services.files

//state in transfer manager
enum class UploadTransferState {
    QUEUED,
    //transferring data
    ACTIVE,
    //disabled by user
    CANCELLED,
    //complete
    COMPLETE,
    //error during some state (upload.error is set)
    ERROR
}