package io.slychat.messenger.core.http.api

/** Triggered by rate limiting. */
class TooManyRequestsException : RuntimeException("Too many requests")