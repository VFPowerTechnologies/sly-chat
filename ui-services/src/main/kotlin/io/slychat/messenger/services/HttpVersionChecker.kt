package io.slychat.messenger.services

import io.slychat.messenger.core.condError
import io.slychat.messenger.core.http.api.versioncheck.CheckResponse
import io.slychat.messenger.core.isNotNetworkError
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
    private var lastResult: VersionCheckResult? = null

    private val subject = BehaviorSubject.create<VersionCheckResult>()

    override val versionCheckResult: Observable<VersionCheckResult>
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

        client.check(clientVersion) mapUi { response ->
            isRunning = false
            updateResult(response)
        } failUi { e ->
            log.condError(isNotNetworkError(e), "Version check failed: {}", e.message, e)
            isRunning = false
        }
    }

    private fun updateResult(response: CheckResponse) {
        log.debug("Version check complete: {}", response.isLatest)

        val result = VersionCheckResult(response.isLatest, response.latestVersion)

        lastResult = result

        subject.onNext(result)
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