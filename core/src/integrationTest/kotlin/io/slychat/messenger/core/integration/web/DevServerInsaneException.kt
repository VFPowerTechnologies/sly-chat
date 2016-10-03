package io.slychat.messenger.core.integration.web

class DevServerInsaneException(
    reason: String,
    cause: Throwable? = null) : RuntimeException("Dev server sanity check failed: $reason", cause)