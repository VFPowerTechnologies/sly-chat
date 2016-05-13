package io.slychat.messenger.core

/** Represents an authorization failure for any service that uses an auth token. */
class UnauthorizedException() : RuntimeException("Authorization failed")
