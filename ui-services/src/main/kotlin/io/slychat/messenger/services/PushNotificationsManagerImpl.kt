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

    private var isWorkInProgress = false

    private var hasCheckedForCurrentAccount = false

    private val isDisabled: Boolean
        get() = pushNotificationService == null

    init {
        if (!isDisabled) {
            appConfigService.updates.filter { it.contains(AppConfig.PUSH_NOTIFICATIONS_TOKEN) }.subscribe { onNewToken() }

            userSessionAvailable.subscribe { userComponent ->
                if (userComponent != null) {
                    currentAccount = userComponent.userLoginData.address
                    authTokenManager = userComponent.authTokenManager
                    hasCheckedForCurrentAccount = false

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

        processWork()
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

    private fun processWork() {
        if (!hasCheckedForCurrentAccount)
            updateTokenForCurrentAccount()

        unregisterTokens()
    }

    //TODO bug
    //we also need to check if the account we're currently running is the new account
    //if not, then we need to try again later
    private fun updateTokenForCurrentAccount() {
        if (isWorkInProgress)
            return

        if (hasCheckedForCurrentAccount)
            return

        if (!isNetworkAvailable)
            return

        val authTokenManager = this.authTokenManager ?: return
        val address = currentAccount ?: return

        val token = appConfigService.pushNotificationsToken ?: return

        hasCheckedForCurrentAccount = true

        if (appConfigService.pushNotificationsRegistrations.contains(address.id)) {
            log.debug("Token already registered for {}", address)
            return
        }

        if (appConfigService.pushNotificationsUnregistrations.contains(address)) {
            log.debug("Was scheduled for unregistration, restoring to registered state")

            appConfigService.withEditor {
                pushNotificationsUnregistrations -= address
                pushNotificationsRegistrations += address.id
            }

            return
        }

        log.info("Registering push notification token for {}", address)

        isWorkInProgress = true

        authTokenManager.bind {
            val request = RegisterRequest(token, pushNotificationService!!, false)
            pushNotificationsClient.register(it, request)
        }.successUi {
            isWorkInProgress = false

            if (it.errorMessage != null) {
                log.error("Failed to register push notification token: {}", it.errorMessage)
            }
            else {
                log.info("Registered push notification token")

                appConfigService.withEditor {
                    pushNotificationsRegistrations += address.id
                }
            }

            processWork()
        }.failUi {
            log.error("Unable to register push notification token: {}", it.message, it)

            isWorkInProgress = false
        }
    }

    private fun invalidateRegistrations() {
        hasCheckedForCurrentAccount = false

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
            pushNotificationsRegistrations -= address.id
        }

        unregisterTokens()
    }

    private fun unregisterTokens() {
        if (!isNetworkAvailable)
            return

        if (isWorkInProgress)
            return

        if (appConfigService.pushNotificationsUnregistrations.isEmpty())
            return

        val token = appConfigService.pushNotificationsToken ?: return

        val address = appConfigService.pushNotificationsUnregistrations.first()

        val request = UnregisterRequest(address, token)

        isWorkInProgress = true

        pushNotificationsClient.unregister(request).successUi {
            isWorkInProgress = false

            log.info("Unregistered token for {}", address)

            appConfigService.withEditor {
                pushNotificationsUnregistrations -= address
            }

            processWork()
        }.failUi {
            isWorkInProgress = false

            log.error("Failed to unregister token for {}: {}", it.message, it)
        }
    }
}