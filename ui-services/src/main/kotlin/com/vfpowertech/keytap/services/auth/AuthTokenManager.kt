package com.vfpowertech.keytap.services.auth

import com.vfpowertech.keytap.services.bindRecoverForUi
import com.vfpowertech.keytap.services.bindUi
import com.vfpowertech.keytap.services.mapUi
import nl.komponents.kovenant.Deferred
import nl.komponents.kovenant.Promise
import nl.komponents.kovenant.deferred
import nl.komponents.kovenant.functional.bind
import nl.komponents.kovenant.functional.map
import org.slf4j.LoggerFactory
import rx.Observable
import rx.subjects.BehaviorSubject
import java.util.*

//XXX what do I do regarding network connectivity? I guess just hand out the token, and then the tasks can fail due to connection issues?
//likewise, any token refresh would end up with an error
class AuthTokenManager(
    private val tokenProvider: TokenProvider
) {
    companion object {
        private const val MAX_RETRIES = 2
    }

    private val log = LoggerFactory.getLogger(javaClass)

    private val newTokenSubject = BehaviorSubject.create<AuthToken?>()
    val newToken: Observable<AuthToken?> = newTokenSubject

    private val queued = ArrayList<Deferred<AuthToken, Exception>>()

    private var currentToken: AuthToken? = null

    init {
        tokenProvider.events.subscribe { onProviderEvent(it) }
    }

    private fun updateCachedToken(authToken: AuthToken?) {
        currentToken = authToken
        newTokenSubject.onNext(authToken)
    }

    /** Used to set an initial token externally. */
    fun setToken(authToken: AuthToken) {
        updateCachedToken(authToken)
    }

    private fun onProviderEvent(event: TokenEvent) {
        log.info("Received event: {}", event)

        when (event) {
            is TokenEvent.Expired -> {
                updateCachedToken(null)
            }

            is TokenEvent.New -> {
                updateCachedToken(event.authToken)
                processQueue()
            }

            is TokenEvent.Error -> {
                updateCachedToken(null)
                failQueue(event.cause)
            }
        }
    }

    private fun processQueue() {
        val token = currentToken ?: return

        queued.forEach { it.resolve(token) }
        queued.clear()
    }

    private fun failQueue(reason: Exception) {
        queued.forEach { it.reject(reason) }
        queued.clear()
    }

    /** Invalid the current token. Can be used by long-running tasks that aren't scoped to a single Promise. */
    fun invalidateToken() {
        //the token provider'll provide us with a new token when it's ready
        if (currentToken == null)
            return

        currentToken = null
        tokenProvider.invalidateToken()
    }

    private fun addToQueue(d: Deferred<AuthToken, Exception>) {
        queued.add(d)
        processQueue()
    }

    //don't like the code dup here, but no real way to not do this
    private fun <T> wrapWithRetryMap(onUiThread:Boolean, what: (AuthToken) -> T, remainingTries: Int): Promise<T, Exception> {
        val d = deferred<AuthToken, Exception>()

        val p = d.promise

        val p2 = if (!onUiThread)
            p map { authToken ->
                what(authToken)
            }
        else
            p mapUi { authToken ->
                what(authToken)
            }

        val p3 = p2 bindRecoverForUi { e: AuthTokenExpiredException ->
            log.debug("Remaining retry attempts: {}", remainingTries)

            if (remainingTries == 0)
                throw e

            invalidateToken()

            wrapWithRetryMap(onUiThread, what, remainingTries-1)
        }

        addToQueue(d)

        return p3

    }

    private fun <T> wrapWithRetryBind(onUiThread:Boolean, what: (AuthToken) -> Promise<T, Exception>, remainingTries: Int): Promise<T, Exception> {
        val d = deferred<AuthToken, Exception>()

        val p = d.promise

        val p2 = if (!onUiThread)
            p bind { authToken ->
                what(authToken)
            }
        else
            p bindUi { authToken ->
                what(authToken)
            }

        val p3 = p2 bindRecoverForUi { e: AuthTokenExpiredException ->
            log.debug("Remaining retry attempts: {}", remainingTries)

            if (remainingTries == 0)
                throw e

            invalidateToken()

            wrapWithRetryBind(onUiThread, what, remainingTries-1)
        }

        addToQueue(d)

        return p3
    }

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
    fun <T> bind(what: (AuthToken) -> Promise<T, Exception>): Promise<T, Exception> {
        return wrapWithRetryBind(false, what, MAX_RETRIES)
    }

    /** Version of bind that runs the given body on the main UI thread. */
    fun <T> bindUi(what: (AuthToken) -> Promise<T, Exception>): Promise<T, Exception> {
        return wrapWithRetryBind(true, what, MAX_RETRIES)
    }

    fun <T> map(what: (AuthToken) -> T): Promise<T, Exception> {
        return wrapWithRetryMap(false, what, MAX_RETRIES)
    }

    fun <T> mapUi(what: (AuthToken) -> T): Promise<T, Exception> {
        return wrapWithRetryMap(true, what, MAX_RETRIES)
    }
}

