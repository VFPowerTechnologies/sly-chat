package io.slychat.messenger.services

import nl.komponents.kovenant.Promise

interface TokenFetcher {
    fun fetch(): Promise<DeviceTokens?, Exception>

    fun canRetry(exception: Exception): Boolean

    /** Whether the exception should be logged at the error level or not. */
    fun isInterestingException(exception: Exception): Boolean
}