package io.slychat.messenger.services

import io.slychat.messenger.services.auth.AuthTokenRefreshResult
import nl.komponents.kovenant.Promise

/** Handles refreshing auth tokens for the currently logged in account. */
interface TokenRefresher {
    fun refreshAuthToken(): Promise<AuthTokenRefreshResult, Exception>
}