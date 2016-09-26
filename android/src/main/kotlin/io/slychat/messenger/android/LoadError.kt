package io.slychat.messenger.android

//only one of errorCode or cause will be provided
data class LoadError(
    val type: LoadErrorType,
    //0 if not provided
    val errorCode: Int,
    val cause: Throwable?
)