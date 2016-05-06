package com.vfpowertech.keytap.core

/** Represents an authorization failure for any service that uses an auth token. */
class UnauthorizedException() : RuntimeException("Authorization failed")
