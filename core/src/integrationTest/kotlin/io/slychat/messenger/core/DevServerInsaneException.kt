package io.slychat.messenger.core

class DevServerInsaneException(
    reason: String,
    cause: Throwable? = null) : RuntimeException("Dev server sanity check failed: $reason", cause)