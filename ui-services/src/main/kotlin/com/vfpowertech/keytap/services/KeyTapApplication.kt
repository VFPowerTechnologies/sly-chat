package com.vfpowertech.keytap.services

import com.vfpowertech.keytap.core.crypto.KeyVault
import com.vfpowertech.keytap.core.persistence.SessionData
import com.vfpowertech.keytap.core.relay.*
import com.vfpowertech.keytap.services.di.*
import nl.komponents.kovenant.Promise
import nl.komponents.kovenant.functional.bind
import nl.komponents.kovenant.functional.map
import nl.komponents.kovenant.ui.successUi
import org.slf4j.LoggerFactory
import rx.Observable
import rx.subjects.BehaviorSubject

class KeyTapApplication {
    private val log = LoggerFactory.getLogger(javaClass)

    private var isNetworkAvailable = false

    private lateinit var reconnectionTimer: ExponentialBackoffTimer

    lateinit var appComponent: ApplicationComponent
        private set

    var userComponent: UserComponent? = null
        private set

    //the following observables never complete or error and are valid for the lifetime of the application
    //only changes in value are emitted from these
    private val networkAvailableSubject = BehaviorSubject.create(false)
    val networkAvailable: Observable<Boolean> = networkAvailableSubject

    private val relayAvailableSubject = BehaviorSubject.create(false)
    val relayAvailable: Observable<Boolean> = relayAvailableSubject

    private val userSessionAvailableSubject = BehaviorSubject.create(false)
    val userSessionAvailable: Observable<Boolean> = userSessionAvailableSubject

    fun init(platformModule: PlatformModule) {
        appComponent = DaggerApplicationComponent.builder()
            .platformModule(platformModule)
            .applicationModule(ApplicationModule(this))
            .build()

        initializeApplicationServices()
    }

    private fun initializeApplicationServices() {
        reconnectionTimer = ExponentialBackoffTimer(appComponent.rxScheduler)

        appComponent.networkStatusService.updates.subscribe {
            updateNetworkStatus(it)
        }
    }

    private fun updateNetworkStatus(isAvailable: Boolean) {
        isNetworkAvailable = isAvailable
        log.info("Network is available: {}", isAvailable)

        networkAvailableSubject.onNext(isAvailable)

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

        //doing disk io here is bad, but...
        createUserPaths(userComponent.userPaths)

        initializeUserSession(userComponent)

        userSessionAvailableSubject.onNext(true)

        return userComponent
    }

    private fun createUserPaths(userPaths: UserPaths) {
        userPaths.accountDir.mkdirs()
    }

    private fun initializeUserSession(userComponent: UserComponent) {
        userComponent.relayClientManager.onlineStatus.subscribe {
            onRelayStatusChange(it)
        }

        userComponent.relayClientManager.events.subscribe { handleRelayClientEvent(it) }

        if (!isNetworkAvailable) {
            log.info("Network unavailable, not connecting to relay")
            return
        }

        connectToRelay(userComponent)
    }

    private fun handleRelayClientEvent(event: RelayClientEvent) {
        when (event) {
            is ConnectionEstablished -> reconnectionTimer.reset()
            is ConnectionLost -> if (!event.wasRequested) reconnectToRelay()
            is ConnectionFailure -> {
                log.warn("Connection to relay failed: {}", event.error.message)
                reconnectToRelay()
            }

            is AuthenticationSuccessful -> {

            }

            is AuthenticationExpired -> {
                log.info("Auth token expired, refreshing")
                refreshAuthToken()
            }

            is AuthenticationFailure -> {
                //first we try and refresh; if that fails we need to prompt the user for a password
                refreshAuthToken()

                //TODO prompt user for password; this can occur if a user changes his password
                //on a diff device while they're online on another device
            }
        }
    }

    private fun refreshAuthToken() {
        val userComponent = userComponent ?: error("No user session")
        val data = userComponent.userLoginData
        data.authToken = null
        val remotePasswordHash = data.keyVault.remotePasswordHash

        val sessionDataPersistenceManager = userComponent.sessionDataPersistenceManager

        appComponent.authenticationService.refreshAuthToken(data.username, remotePasswordHash) bind { response ->
            log.info("Got new auth token")
            sessionDataPersistenceManager.store(SessionData(response.authToken)) map { response }
        } successUi { response ->
            data.authToken = response.authToken

            //TODO key regen
            reconnectToRelay()
        } fail { e ->
            log.error("Unable to refresh auth token", e)
        }
    }

    private fun reconnectToRelay() {
        log.info("Attempting to reconnect to relay")
        reconnectionTimer.next().subscribe {
            val userComponent = this.userComponent
            if (userComponent != null) {
                connectToRelay(userComponent)
            }
            else
                log.warn("No longer logged in, aborting reconnect")
        }

        log.info("Reconnecting in {}s", reconnectionTimer.waitTimeSeconds)
    }

    private fun onRelayStatusChange(newStatus: Boolean) {
        relayAvailableSubject.onNext(newStatus)
    }

    /** Fetches auth token if none is given, then connects to the relay. */
    private fun connectToRelay(userComponent: UserComponent) {
        val userLoginData = userComponent.userLoginData
        if (userLoginData.authToken == null) {
            log.info("No auth token, fetching new")
            refreshAuthToken()
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
            log.warn("User session has already been terminated")
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

    fun storeAccountData(keyVault: KeyVault): Promise<Unit, Exception> {
        val userComponent = this.userComponent ?: error("No user session")

        return userComponent.keyVaultPersistenceManager.store(keyVault) fail { e ->
            log.error("Unable to store account data: {}", e.message, e)
        }
    }
}
