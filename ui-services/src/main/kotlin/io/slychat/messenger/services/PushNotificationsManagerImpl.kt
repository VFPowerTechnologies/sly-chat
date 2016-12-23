package io.slychat.messenger.services

import io.slychat.messenger.core.SlyAddress
import io.slychat.messenger.core.http.api.pushnotifications.PushNotificationService
import io.slychat.messenger.core.http.api.pushnotifications.PushNotificationsAsyncClient
import io.slychat.messenger.core.http.api.pushnotifications.RegisterRequest
import io.slychat.messenger.core.http.api.pushnotifications.UnregisterRequest
import io.slychat.messenger.services.auth.AuthTokenManager
import io.slychat.messenger.services.config.AppConfig
import io.slychat.messenger.services.config.AppConfigService
import io.slychat.messenger.services.di.UserComponent
import nl.komponents.kovenant.ui.failUi
import nl.komponents.kovenant.ui.successUi
import org.slf4j.LoggerFactory
import rx.Observable

//TODO need to handle retries
class PushNotificationsManagerImpl(
    tokenUpdates: Observable<String>,
    userSessionAvailable: Observable<UserComponent?>,
    networkStatus: Observable<Boolean>,
    private val pushNotificationService: PushNotificationService?,
    private val appConfigService: AppConfigService,
    private val pushNotificationsClient: PushNotificationsAsyncClient
) : PushNotificationsManager {
    private val log = LoggerFactory.getLogger(javaClass)

    private var currentAccount: SlyAddress? = null

    private var authTokenManager: AuthTokenManager? = null

    private var isNetworkAvailable = false

    private var isRegistrationInProgress = false

    private var isUnregistrationInProgress = false

    private val isLoggedIn: Boolean
        get() = authTokenManager != null

    private val isDisabled: Boolean
        get() = pushNotificationService == null

    init {
        if (!isDisabled) {
            appConfigService.updates.filter { it.contains(AppConfig.PUSH_NOTIFICATIONS_TOKEN) }.subscribe { onNewToken() }

            //TODO check if we need to update the token for this user
            userSessionAvailable.subscribe { userComponent ->
                if (userComponent != null) {
                    currentAccount = userComponent.userLoginData.address
                    authTokenManager = userComponent.authTokenManager
                    updateTokenForCurrentAccount()
                } else {
                    currentAccount = null
                    authTokenManager = null
                }
            }

            tokenUpdates.subscribe { onTokenUpdate(it) }

            networkStatus.subscribe { onNetworkStatusUpdate(it) }
        }
    }

    override fun init() {
    }

    private fun onNetworkStatusUpdate(isAvailable: Boolean) {
        isNetworkAvailable = isAvailable

        if (!isAvailable)
            return

        updateTokenForCurrentAccount()

        unregisterTokens()
    }

    private fun onTokenUpdate(token: String?) {
        log.info("Updating token")

        appConfigService.withEditor {
            pushNotificationsToken = token
        }
    }

    private fun onNewToken() {
        log.info("Received new token")

        invalidateRegistrations()

        updateTokenForCurrentAccount()
    }

    private fun updateTokenForCurrentAccount() {
        if (!isNetworkAvailable)
            return

        val authTokenManager = this.authTokenManager ?: return
        val address = currentAccount ?: return

        val token = appConfigService.pushNotificationsToken ?: return

        if (appConfigService.pushNotificationsRegistrations.contains(address.id)) {
            log.debug("Token already registered for {}", address)
            return
        }

        log.info("Registering push notification token for {}", address)

        isRegistrationInProgress = true

        authTokenManager.bind {
            val request = RegisterRequest(token, pushNotificationService!!, false)
            pushNotificationsClient.register(it, request)
        }.successUi {
            isRegistrationInProgress = false

            if (it.errorMessage != null) {
                log.error("Failed to register push notification token: {}", it.errorMessage)
            }
            else {
                log.info("Registered push notification token")

                appConfigService.withEditor {
                    pushNotificationsRegistrations += address.id
                }
            }
        }.failUi {
            log.error("Unable to register push notification token: {}", it.message, it)
            isRegistrationInProgress = false
        }
    }

    private fun invalidateRegistrations() {
        appConfigService.withEditor {
            pushNotificationsRegistrations = emptySet()
        }
    }

    override fun unregister(address: SlyAddress) {
        if (isDisabled)
            error("unregister() called but push notification system is disabled")

        if (appConfigService.pushNotificationsToken == null) {
            log.warn("Attempt to add unregistration but no token is set")
            return
        }

        log.info("Queuing unregistration for {}", address)

        appConfigService.withEditor {
            pushNotificationsUnregistrations += address
        }

        unregisterTokens()
    }

    private fun unregisterTokens() {
        if (!isNetworkAvailable)
            return

        if (isUnregistrationInProgress)
            return

        if (appConfigService.pushNotificationsUnregistrations.isEmpty())
            return

        val token = appConfigService.pushNotificationsToken ?: return

        val address = appConfigService.pushNotificationsUnregistrations.first()

        val request = UnregisterRequest(address, token)

        pushNotificationsClient.unregister(request).successUi {
            log.info("Unregistered token for {}", address)

            appConfigService.withEditor {
                pushNotificationsUnregistrations -= address
            }

            isUnregistrationInProgress = false

            //keep processing
            unregisterTokens()
        }.failUi {
            log.error("Failed to unregister token for {}: {}", it.message, it)

            isUnregistrationInProgress = false
        }
    }
}