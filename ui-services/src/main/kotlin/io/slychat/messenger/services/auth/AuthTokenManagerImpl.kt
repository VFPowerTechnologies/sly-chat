package io.slychat.messenger.services.auth

import io.slychat.messenger.core.AuthToken
import io.slychat.messenger.core.SlyAddress
import io.slychat.messenger.core.UnauthorizedException
import io.slychat.messenger.core.UserCredentials
import io.slychat.messenger.services.bindRecoverForUi
import io.slychat.messenger.services.bindUi
import io.slychat.messenger.services.contacts.PromiseTimerFactory
import io.slychat.messenger.services.mapUi
import nl.komponents.kovenant.Deferred
import nl.komponents.kovenant.Promise
import nl.komponents.kovenant.deferred
import nl.komponents.kovenant.functional.bind
import nl.komponents.kovenant.functional.map
import org.slf4j.LoggerFactory
import rx.Observable
import rx.Scheduler
import rx.subjects.BehaviorSubject
import java.util.*
import java.util.concurrent.TimeUnit

//XXX what do I do regarding network connectivity? I guess just hand out the token, and then the tasks can fail due to connection issues?
//likewise, any token refresh would end up with an error
class AuthTokenManagerImpl(
    private val address: SlyAddress,
    private val tokenProvider: TokenProvider,
    private val promiseTimerFactory: PromiseTimerFactory,
    scheduler: Scheduler
) : AuthTokenManager {
    companion object {
        internal const val MAX_RETRIES = 2
    }

    private val log = LoggerFactory.getLogger(javaClass)

    private val newTokenSubject = BehaviorSubject.create<AuthToken?>()
    override val newToken: Observable<AuthToken?> = newTokenSubject.observeOn(scheduler)

    //queued and currentToken are synchronized as they can end up being access from any thread
    private var queued = ArrayList<Deferred<AuthToken, Exception>>()

    private var currentToken: AuthToken? = null

    init {
        tokenProvider.events.subscribe { onProviderEvent(it) }
    }

    private fun updateCachedToken(authToken: AuthToken?) {
        synchronized(this) {
            currentToken = authToken
            newTokenSubject.onNext(authToken)
        }
    }

    override fun setToken(authToken: AuthToken) {
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
        synchronized(this) {
            val token = currentToken ?: return

            //for testing in sync mode, need to prevent concurrent modification to the queue
            val old = queued
            queued = ArrayList()

            old.forEach { it.resolve(token) }
        }
    }

    private fun failQueue(reason: Exception) {
        synchronized(this) {
            val old = queued
            queued = ArrayList()

            old.forEach { it.reject(reason) }
        }
    }

    override fun invalidateToken() {
        synchronized(this) {
            currentToken = null
        }
        tokenProvider.invalidateToken()
    }

    private fun addToQueue(d: Deferred<AuthToken, Exception>) {
        synchronized(this) {
            queued.add(d)
            processQueue()
        }
    }

    private fun nextRetryTimeout(attemptN: Int): Promise<Unit, Exception> {
        val n = attemptN + 1
        val exp = Math.pow(2.0, n.toDouble())
        val secs = Random().nextInt(exp.toInt() + 1).toLong()

        return promiseTimerFactory.run(secs, TimeUnit.SECONDS)
    }

    //don't like the code dup here, but no real way to not do this
    private fun <T> wrapWithRetryMap(onUiThread: Boolean, what: (UserCredentials) -> T, remainingTries: Int): Promise<T, Exception> {
        val d = deferred<AuthToken, Exception>()

        val p = d.promise

        val p2 = if (!onUiThread)
            p map { authToken ->
                what(UserCredentials(address, authToken))
            }
        else
            p mapUi { authToken ->
                what(UserCredentials(address, authToken))
            }

        val p3 = p2 bindRecoverForUi { e: UnauthorizedException ->
            log.debug("Remaining retry attempts: {}", remainingTries)

            if (remainingTries == 0)
                throw e

            invalidateToken()

            val attemptN = MAX_RETRIES - remainingTries
            nextRetryTimeout(attemptN) bind { wrapWithRetryMap(onUiThread, what, remainingTries - 1) }
        }

        addToQueue(d)

        return p3
    }

    private fun <T> wrapWithRetryBind(onUiThread: Boolean, what: (UserCredentials) -> Promise<T, Exception>, remainingTries: Int): Promise<T, Exception> {
        val d = deferred<AuthToken, Exception>()

        val p = d.promise

        val p2 = if (!onUiThread)
            p bind { authToken ->
                what(UserCredentials(address, authToken))
            }
        else
            p bindUi { authToken ->
                what(UserCredentials(address, authToken))
            }

        val p3 = p2 bindRecoverForUi { e: UnauthorizedException ->
            log.debug("Remaining retry attempts: {}", remainingTries)

            if (remainingTries == 0)
                throw e

            invalidateToken()

            val attemptN = MAX_RETRIES - remainingTries
            nextRetryTimeout(attemptN) bind { wrapWithRetryBind(onUiThread, what, remainingTries - 1) }
        }

        addToQueue(d)

        return p3
    }

    override fun <T> bind(what: (UserCredentials) -> Promise<T, Exception>): Promise<T, Exception> {
        return wrapWithRetryBind(false, what, MAX_RETRIES)
    }

    override fun <T> bindUi(what: (UserCredentials) -> Promise<T, Exception>): Promise<T, Exception> {
        return wrapWithRetryBind(true, what, MAX_RETRIES)
    }

    override fun <T> map(what: (UserCredentials) -> T): Promise<T, Exception> {
        return wrapWithRetryMap(false, what, MAX_RETRIES)
    }

    override fun <T> mapUi(what: (UserCredentials) -> T): Promise<T, Exception> {
        return wrapWithRetryMap(true, what, MAX_RETRIES)
    }
}