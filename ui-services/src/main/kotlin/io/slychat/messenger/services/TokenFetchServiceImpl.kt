package io.slychat.messenger.services

import io.slychat.messenger.core.condError
import io.slychat.messenger.services.config.AppConfigService
import nl.komponents.kovenant.ui.failUi
import nl.komponents.kovenant.ui.successUi
import org.slf4j.LoggerFactory
import rx.Observable
import rx.subjects.PublishSubject
import java.util.concurrent.TimeUnit

//TODO retry on error
class TokenFetchServiceImpl(
    private val isEnabled: Boolean,
    private val fetcher: TokenFetcher,
    private val appConfigService: AppConfigService,
    networkAvailability: Observable<Boolean>,
    private val retryTime: Long,
    private val retryTimeUnit: TimeUnit
) : TokenFetchService {
    private val log = LoggerFactory.getLogger(javaClass)

    private var isFetching = false
    private var isFetched = false
    private var isNetworkAvailable = false

    private val tokenSubject = PublishSubject.create<DeviceTokens?>()
    override val tokenUpdates: Observable<DeviceTokens?>
        get() = tokenSubject

    init {
        if (isEnabled)
            networkAvailability.subscribe { onNetworkStatusChange(it) }
    }

    private fun onNetworkStatusChange(isAvailable: Boolean) {
        isNetworkAvailable = isAvailable

        fetch()
    }

    private fun fetch() {
        if (isFetched) {
            log.info("Token has already been fetched")
            return
        }

        if (!isNetworkAvailable) {
            log.info("Fetch requested but no network is available")
            return
        }

        if (!appConfigService.pushNotificationsPermRequested) {
            log.info("Push notification permissions not requested, not doing anything")
            return
        }

        isFetching = true

        fetcher.fetch().successUi {
            isFetching = false
            isFetched = true

            log.info("Token received")

            tokenSubject.onNext(it)
        }.failUi {
            //TODO retry timer
            isFetching = false

            log.condError(fetcher.isInterestingException(it), "Failed to fetch token: {}", it.message, it)
        }
    }

    override fun refresh() {
        if (!isEnabled)
            error("TokenFetchServiceImpl is disabled")

        isFetched = false
        fetch()
    }
}