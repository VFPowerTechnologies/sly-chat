package io.slychat.messenger.services.auth

import io.slychat.messenger.services.AuthApiResponseException
import io.slychat.messenger.services.TokenRefresher
import nl.komponents.kovenant.ui.alwaysUi
import nl.komponents.kovenant.ui.failUi
import nl.komponents.kovenant.ui.successUi
import org.slf4j.LoggerFactory
import rx.Observable
import rx.subjects.PublishSubject

class TokenRefresherTokenProvider(
    private val tokenRefresher: TokenRefresher
) : TokenProvider {
    private val log = LoggerFactory.getLogger(javaClass)

    private val eventsSubject = PublishSubject.create<TokenEvent>()
    override val events: Observable<TokenEvent>
        get() = eventsSubject

    private var running = false

    override fun invalidateToken() {
        if (running)
            return

        running = true

        eventsSubject.onNext(TokenEvent.Expired())

        tokenRefresher.refreshAuthToken() successUi { response ->
            log.info("Refreshed auth token")
            eventsSubject.onNext(TokenEvent.New(response.authToken))
        } failUi { e ->
            if (e is AuthApiResponseException)
                log.warn("Unable to get new auth token: {}", e, e.message)
            else
                log.error("Unable to get new auth token: {}", e, e.message)

            eventsSubject.onNext(TokenEvent.Error(e))
        } alwaysUi {
            running = false
        }
    }
}