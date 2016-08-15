package io.slychat.messenger.services

import nl.komponents.kovenant.Promise

/** Handles refreshing auth tokens for the currently logged in account. */
interface TokenRefresher {
    fun refreshAuthToken(): Promise<AuthTokenRefreshResult, Exception>
}