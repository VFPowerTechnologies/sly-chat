package io.slychat.messenger.services.auth

import rx.Observable

/**
 * Handles fetching new auth tokens.
 *
 * Providers should be properly configured to timeout, as tasks that request tokens are queued until a token is
 * available or an error occurs.
 */
interface TokenProvider {
    /** Events must occur on the main thread. */
    val events: Observable<TokenEvent>

    /** Called to mark the current token as invalid. Should attempt to request a new token if possible. This function
     * may be called multiple times when a token expires. */
    fun invalidateToken()
}


