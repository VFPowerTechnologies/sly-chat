package com.vfpowertech.keytap.ui.services

import com.vfpowertech.keytap.core.crypto.KeyVault
import com.vfpowertech.keytap.ui.services.di.*
import org.slf4j.LoggerFactory
import rx.Observable
import rx.subjects.BehaviorSubject

class UserLoginData(
    val username: String,
    val keyVault: KeyVault,
    //may be null if refreshing (manipulated by the MessengerService)
    var authToken: String?
)

class NoClientException : RuntimeException("No client is currently active")
class ReauthenticationRequiredException : Exception("Reauthentication is required")

class KeyTapApplication {
    private val log = LoggerFactory.getLogger(javaClass)

    private var isNetworkAvailable = false

    lateinit var appComponent: ApplicationComponent
        private set

    var userComponent: UserComponent? = null
        private set

    private val userSessionAvailableSubject = BehaviorSubject.create(false)
    /** Never completes. */
    val userSessionAvailable: Observable<Boolean> = userSessionAvailableSubject

    fun init(platformModule: PlatformModule) {
        appComponent = DaggerApplicationComponent.builder()
            .platformModule(platformModule)
            .applicationModule(ApplicationModule(this))
            .build()

        initializeApplicationServices()
    }

    private fun initializeApplicationServices() {
        appComponent.networkStatusService.updates.subscribe {
            updateNetworkStatus(it)
        }
    }

    private fun updateNetworkStatus(isAvailable: Boolean) {
        isNetworkAvailable = isAvailable
        log.info("Network is available: {}", isAvailable)

        //do nothing if we're not logged in
        val userComponent = this.userComponent ?: return

        //TODO trigger remote relay login if we're online now
    }

    fun createUserSession(userLoginData: UserLoginData): UserComponent {
        if (userComponent != null)
            error("UserComponent already loaded")

        log.info("Creating user session")

        val userComponent = appComponent.plus(UserModule(userLoginData))
        this.userComponent = userComponent

        initializeUserSession(userComponent)

        userSessionAvailableSubject.onNext(true)

        return userComponent
    }

    private fun initializeUserSession(userComponent: UserComponent) {
        userComponent.relayClientManager.onlineStatus.subscribe {
            //TODO
        }

        if (!isNetworkAvailable) {
            log.info("Network unavailable, not connecting to relay")
            return
        }

        connectToRelay(userComponent)
    }

    /** Fetches auth token if none is given, then connects to the relay. */
    private fun connectToRelay(userComponent: UserComponent) {
        val userLoginData = userComponent.userLoginData
        if (userLoginData.authToken == null) {
            log.info("No auth token, fetching new")
            //TODO have loginserviceimpl use something under it so we don't have to use ui-facing services directly
            error("Not implemented")
        }
        else
            doRelayLogin(null)
    }

    /**
     * Actually log into the relay server.
     *
     * If an authToken is given, it's used to overwrite the currently set auth token.
     */
    private fun doRelayLogin(authToken: String?) {
        val userComponent = this.userComponent
        if (userComponent == null) {
            println("User session has already been terminated")
            return
        }

        if (authToken != null)
            userComponent.userLoginData.authToken = authToken

        userComponent.relayClientManager.connect()
    }

    private fun deinitializeUserSession(userComponent: UserComponent) {
        userComponent.sqlitePersistenceManager.shutdown()
        userComponent.relayClientManager.disconnect()
    }

    fun destroyUserSession() {
        val userComponent = this.userComponent ?: return

        log.info("Destroying user session")

        //notify listeners before tearing down session
        userSessionAvailableSubject.onNext(false)

        //TODO shutdown stuff; probably should return a promise
        deinitializeUserSession(userComponent)

        this.userComponent = null
    }

    fun shutdown() {
        destroyUserSession()
    }
}
