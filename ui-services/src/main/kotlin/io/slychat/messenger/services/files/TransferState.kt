package io.slychat.messenger.services.files

//state in transfer manager
enum class TransferState {
    QUEUED,
    //transferring data
    ACTIVE,
    //active and pending cancel (typically required if something needs to be done remotely for cancellation)
    CANCELLING,
    //disabled by user
    CANCELLED,
    //complete
    COMPLETE,
    //error during some state (upload.error/download.error is set)
    ERROR
}