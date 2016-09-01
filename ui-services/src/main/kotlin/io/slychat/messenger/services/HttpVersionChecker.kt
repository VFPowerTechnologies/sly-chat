package io.slychat.messenger.services

import nl.komponents.kovenant.ui.failUi
import org.slf4j.LoggerFactory
import rx.Observable
import rx.Subscription
import rx.subjects.BehaviorSubject

/** Runs a check on init and caches the value indefinitely. */
class HttpVersionChecker(
    private val clientVersion: String,
    networkAvailable: Observable<Boolean>,
    private val clientVersionAsyncClientFactory: ClientVersionAsyncClientFactory
) : VersionChecker {
    private val log = LoggerFactory.getLogger(javaClass)

    private var isInitialized = false
    private var isNetworkAvailable = false

    private var isRunning = false
    private var lastResult: Boolean? = null

    private val subject = BehaviorSubject.create<Unit>()

    override val versionOutOfDate: Observable<Unit>
        get() = subject

    private var subscription: Subscription? = null

    init {
        subscription = networkAvailable.subscribe { onNetworkStatusChange(it) }
    }

    private fun onNetworkStatusChange(isAvailable: Boolean) {
        isNetworkAvailable = isAvailable

        if (!isInitialized)
            return

        runCheck()
    }

    private fun runCheck() {
        if (!isNetworkAvailable || isRunning || lastResult != null)
            return

        log.debug("Running version check")

        isRunning = true

        val client = clientVersionAsyncClientFactory.create()

        client.check(clientVersion) mapUi { isUpToDate ->
            isRunning = false
            updateResult(isUpToDate)
        } failUi { e ->
            log.error("Version check failed: {}", e.message, e)
            isRunning = false
        }
    }

    private fun updateResult(isUpToDate: Boolean) {
        log.debug("Version check complete: {}", isUpToDate)

        lastResult = isUpToDate

        if (!isUpToDate)
            subject.onNext(Unit)
    }

    override fun init() {
        isInitialized = true

        runCheck()
    }

    override fun shutdown() {
        subscription?.unsubscribe()
        subscription = null
    }
}