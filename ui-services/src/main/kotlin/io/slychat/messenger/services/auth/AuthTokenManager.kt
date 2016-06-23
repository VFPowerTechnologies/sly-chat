package io.slychat.messenger.services.auth

import io.slychat.messenger.core.AuthToken
import io.slychat.messenger.core.UserCredentials
import nl.komponents.kovenant.Promise
import rx.Observable

interface AuthTokenManager {
    val newToken: Observable<AuthToken?>

    /** Used to set an initial token externally. */
    fun setToken(authToken: AuthToken)

    /** Invalid the current token. Can be used by long-running tasks that aren't scoped to a single Promise. */
    fun invalidateToken()

    /**
     * Queues a task to be when a token is available. Tasks will be rerun if the provided token is expired, so they
     * should be idempotent or take this behavior into consideration.
     *
     * If a token is available, the task is run.
     *
     * If a token is not available, the task is queued to be run as soon as a token refresh result comes in.
     * If a new token is successfully received, the task will be run with the new token.
     * If a new token isn't successfully received, the task will fail with the token fetch failure exception.
     *
     * If the task raises AuthTokenExpiredException, an attempt to fetch a new token and rerun the task will occur. This
     * may occur multiple times.
     */
    fun <T> bind(what: (UserCredentials) -> Promise<T, Exception>): Promise<T, Exception>

    /** Version of bind that runs the given body on the main UI thread. */
    fun <T> bindUi(what: (UserCredentials) -> Promise<T, Exception>): Promise<T, Exception>

    fun <T> map(what: (UserCredentials) -> T): Promise<T, Exception>

    fun <T> mapUi(what: (UserCredentials) -> T): Promise<T, Exception>
}
