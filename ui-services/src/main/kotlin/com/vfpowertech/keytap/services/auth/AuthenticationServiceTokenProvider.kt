package com.vfpowertech.keytap.services.auth

import com.vfpowertech.keytap.core.crypto.KeyVault
import com.vfpowertech.keytap.core.persistence.AccountInfo
import com.vfpowertech.keytap.services.AuthenticationService
import com.vfpowertech.keytap.services.KeyTapApplication
import nl.komponents.kovenant.ui.alwaysUi
import nl.komponents.kovenant.ui.failUi
import nl.komponents.kovenant.ui.successUi
import org.slf4j.LoggerFactory
import rx.Observable
import rx.subjects.PublishSubject

class AuthenticationServiceTokenProvider(
    private val application: KeyTapApplication,
    private val accountInfo: AccountInfo,
    private val keyVault: KeyVault,
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
            accountInfo,
            application.installationData.registrationId,
            keyVault.remotePasswordHash
        ) successUi { response ->
            log.info("Refreshed auth token")
            //TODO maybe move AuthToken out to core as a wrapped type?
            eventsSubject.onNext(TokenEvent.New(AuthToken(response.authToken)))
        } failUi { e ->
            log.error("Unable to get new auth token: {}", e, e.message)
            eventsSubject.onNext(TokenEvent.Error(e))
        } alwaysUi {
            running = false
        }
    }
}