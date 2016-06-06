package io.slychat.messenger.core.sentry

class SentryException(message: String, cause: Throwable?, val isRecoverable: Boolean) : Exception(message, cause) {
    constructor(message: String, isRecoverable: Boolean) : this(message, null, isRecoverable)
}