package io.slychat.messenger.services.auth

import io.slychat.messenger.services.AuthenticationService
import io.slychat.messenger.services.KeyTapApplication
import io.slychat.messenger.services.UserData
import nl.komponents.kovenant.ui.alwaysUi
import nl.komponents.kovenant.ui.failUi
import nl.komponents.kovenant.ui.successUi
import org.slf4j.LoggerFactory
import rx.Observable
import rx.subjects.PublishSubject

class AuthenticationServiceTokenProvider(
    private val application: KeyTapApplication,
    private val userLoginData: UserData,
    private val authenticationService: AuthenticationService
) : TokenProvider {
    private val log = LoggerFactory.getLogger(javaClass)

    private val eventsSubject = PublishSubject.create<TokenEvent>()
    override val events: Observable<TokenEvent> = eventsSubject

    private var running = false

    init {
        application.networkAvailable.subscribe {  }
    }

    override fun invalidateToken() {
        if (running)
            return

        running = true

        eventsSubject.onNext(TokenEvent.Expired())

        authenticationService.refreshAuthToken(
            userLoginData.address,
            application.installationData.registrationId,
            userLoginData.keyVault.remotePasswordHash
        ) successUi { response ->
            log.info("Refreshed auth token")
            eventsSubject.onNext(TokenEvent.New(response.authToken))
        } failUi { e ->
            log.error("Unable to get new auth token: {}", e, e.message)
            eventsSubject.onNext(TokenEvent.Error(e))
        } alwaysUi {
            running = false
        }
    }
}