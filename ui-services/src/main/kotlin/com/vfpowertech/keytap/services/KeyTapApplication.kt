package com.vfpowertech.keytap.services

import com.fasterxml.jackson.core.JsonParseException
import com.vfpowertech.keytap.core.KeyTapAddress
import com.vfpowertech.keytap.core.crypto.KeyVault
import com.vfpowertech.keytap.core.div
import com.vfpowertech.keytap.core.persistence.AccountInfo
import com.vfpowertech.keytap.core.persistence.InstallationData
import com.vfpowertech.keytap.core.persistence.SessionData
import com.vfpowertech.keytap.core.persistence.StartupInfo
import com.vfpowertech.keytap.core.persistence.json.JsonAccountInfoPersistenceManager
import com.vfpowertech.keytap.core.persistence.json.JsonInstallationDataPersistenceManager
import com.vfpowertech.keytap.core.persistence.json.JsonSessionDataPersistenceManager
import com.vfpowertech.keytap.core.persistence.json.JsonStartupInfoPersistenceManager
import com.vfpowertech.keytap.core.relay.*
import com.vfpowertech.keytap.services.auth.AuthToken
import com.vfpowertech.keytap.services.di.*
import nl.komponents.kovenant.Promise
import nl.komponents.kovenant.functional.bind
import nl.komponents.kovenant.functional.map
import nl.komponents.kovenant.task
import nl.komponents.kovenant.ui.failUi
import nl.komponents.kovenant.ui.successUi
import org.slf4j.LoggerFactory
import rx.Observable
import rx.Subscription
import rx.subjects.BehaviorSubject
import java.util.*
import java.util.concurrent.TimeUnit

class KeyTapApplication {
    private val log = LoggerFactory.getLogger(javaClass)

    var isNetworkAvailable = false
        private set

    private var reconnectionTimerSubscription: Subscription? = null
    private lateinit var reconnectionTimer: ExponentialBackoffTimer

    lateinit var appComponent: ApplicationComponent
        private set

    var userComponent: UserComponent? = null
        private set

    private var isInitialized = false
    private val onInitListeners = ArrayList<(KeyTapApplication) -> Unit>()

    //the following observables never complete or error and are valid for the lifetime of the application
    //only changes in value are emitted from these
    private val networkAvailableSubject = BehaviorSubject.create(false)
    val networkAvailable: Observable<Boolean> = networkAvailableSubject

    private val relayAvailableSubject = BehaviorSubject.create(false)
    val relayAvailable: Observable<Boolean> = relayAvailableSubject

    private val userSessionAvailableSubject = BehaviorSubject.create(false)
    val userSessionAvailable: Observable<Boolean> = userSessionAvailableSubject

    private val contactListSyncingSubject = BehaviorSubject.create(false)
    val contactListSyncing: Observable<Boolean> = contactListSyncingSubject

    private var contactsSyncSub: Subscription? = null

    private var newTokenSyncSub: Subscription? = null

    private val loginEventsSubject = BehaviorSubject.create<LoginEvent>()
    val loginEvents: Observable<LoginEvent> = loginEventsSubject

    var loginState: LoginState = LoginState.LOGGED_OUT
        private set

    lateinit var installationData: InstallationData

    private var pushingPreKeys = false

    val isAuthenticated: Boolean
        get() = userComponent != null

    fun init(platformModule: PlatformModule) {
        appComponent = DaggerApplicationComponent.builder()
            .platformModule(platformModule)
            .applicationModule(ApplicationModule(this))
            .build()

        initializeApplicationServices()

        initInstallationData()

        //android can fire these events multiple time in succession (eg: when google account sync is occuring)
        //so we clamp down the number of events we process
        appComponent.platformContacts.contactsUpdated.debounce(4000, TimeUnit.MILLISECONDS).subscribe {
            onPlatformContactsUpdated()
        }
    }

    private fun onPlatformContactsUpdated() {
        val userComponent = userComponent ?: return

        log.debug("Platform contacts updated")

        userComponent.contactSyncManager.localSync()
    }

    //XXX this is kinda bad since we block on the main thread, but it's only done once during init anyways
    fun initInstallationData() {
        val path = appComponent.platformInfo.appFileStorageDirectory / "installation-data.json"

        val persistenceManager = JsonInstallationDataPersistenceManager(path)

        val maybeInstallationData = try {
            persistenceManager.retrieve().get()
        }
        catch (e: JsonParseException) {
            log.error("Installation data is corrupted: {}", e.message, e)
            null
        }

        installationData = if (maybeInstallationData != null) {
            maybeInstallationData
        }
        else {
            val generated = InstallationData.generate()
            persistenceManager.store(generated).get()
            generated
        }

        log.info("Installation ID: {}; registration ID: {}", installationData.installationId, installationData.registrationId)
    }

    private fun initializeApplicationServices() {
        reconnectionTimer = ExponentialBackoffTimer(appComponent.rxScheduler)
    }

    private fun emitLoginEvent(event: LoginEvent) {
        //we don't wanna stay in a failed state; it's either this or have the ui work around the
        //logout event being sent after which is hackier
        if (event.state == LoginState.LOGIN_FAILED)
            loginState = LoginState.LOGGED_OUT
        else
            loginState = event.state

        loginEventsSubject.onNext(event)
    }

    /**
     * Attempts to auto-login, if saved account data exists.
     *
     * Must be called to initialize loginEvents, and thus the UI.
    */
    fun autoLogin() {
        if (loginState != LoginState.LOGGED_OUT) {
            log.warn("Attempt to call autoLogin() while state was {}", loginState)
            return
        }

        val userPathsGenerator = appComponent.userPathsGenerator

        val path = userPathsGenerator.startupInfoPath
        val startupInfoPersistenceManager = JsonStartupInfoPersistenceManager(path)

        //XXX this is kinda inefficient, since we already have the userid, then we fetch the email to pass to the normal login functions
        startupInfoPersistenceManager.retrieve() map { startupInfo ->
            if (startupInfo != null) {
                val accountInfoPath = userPathsGenerator.getAccountInfoPath(startupInfo.lastLoggedInAccount)
                val accountInfo = JsonAccountInfoPersistenceManager(accountInfoPath).retrieveSync()
                if (accountInfo != null)
                    AutoLoginInfo(accountInfo.email, startupInfo.savedAccountPassword)
                else
                    null
            }
            else
                null
        } successUi { autoLoginInfo ->
            if (autoLoginInfo != null)
                //don't update the info since it's not needed
                login(autoLoginInfo.username, autoLoginInfo.password, false)
            else
                emitLoginEvent(LoggedOut())
            initializationComplete()
        } failUi { e ->
            log.error("Unable to read startup info: {}", e.message, e)
            emitLoginEvent(LoggedOut())
            initializationComplete()
        }
    }

    private fun schedulePreKeyUpload(keyRegenCount: Int) {
        if (pushingPreKeys || keyRegenCount <= 0)
            return

        val userComponent = this.userComponent
        if (userComponent == null) {
            log.warn("schedulePreKeyUpload called without a user session")
            return
        }

        userComponent.preKeyManager.scheduleUpload(keyRegenCount)
    }

    /**
     * Attempts to login (either locally or remotely) using the given username and password.
     *
     * Emits LoggedIn or LoginFailed.
     */
    fun login(username: String, password: String, rememberMe: Boolean) {
        if (loginState != LoginState.LOGGED_OUT) {
            log.warn("Attempt to call login() while state was {}", loginState)
            return
        }

        emitLoginEvent(LoggingIn())

        //TODO this only works if an email is given; we need to somehow keep a list of phone numbers -> emails somewhere
        //maybe just search every account dir available and find a number that way? kinda rough but works

        //if the unlock fails, we try remotely; this can occur if the password was changed remotely from another device
        appComponent.authenticationService.auth(username, password, installationData.registrationId) bindUi { response ->
            val keyVault = response.keyVault
            //TODO need to put the username in the login response if the user used their phone number
            val address = KeyTapAddress(response.accountInfo.id, response.accountInfo.deviceId)
            val userLoginData = UserLoginData(address, keyVault)
            val userComponent = createUserSession(userLoginData, response.accountInfo)

            if (response.authToken != null)
                userComponent.authTokenManager.setToken(AuthToken(response.authToken))

            contactsSyncSub = userComponent.contactSyncManager.status.subscribe {
                contactListSyncingSubject.onNext(it)
            }

            newTokenSyncSub = userComponent.authTokenManager.newToken.subscribe {
                onNewToken(it)
            }

            //until this finishes, nothing in the UserComponent should be touched
            backgroundInitialization(userComponent, response.authToken, password, rememberMe) mapUi {
                finalizeInit(userComponent, response.keyRegenCount)
            }
        } failUi { e ->
            //incase session initialization failed we need to clean up the user session here
            destroyUserSession()

            val ev = when (e) {
                is AuthApiResponseException ->
                    LoginFailed(e.errorMessage, null)
                else ->
                    LoginFailed(null, e)
            }

            emitLoginEvent(ev)
        }
    }

    private fun onNewToken(authToken: AuthToken?) {
        val userComponent = userComponent ?: return

        //TODO delete
        if (authToken == null)
            return

        log.info("Updating on-disk session data")

        val sessionDataPersistenceManager = userComponent.sessionDataPersistenceManager
        sessionDataPersistenceManager.store(SessionData(authToken.string)) fail { e ->
            log.error("Unable to write session data to disk: {}", e.message, e)
        }

        if (!userComponent.relayClientManager.isOnline)
            connectToRelay()
    }

    /**
     * Log out of the current session. Meant to be called when the user explicitly requests to terminate a session.
     *
     * Emits LoggedOut.
     */
    fun logout() {
        if (destroyUserSession()) {
            emitLoginEvent(LoggedOut())
            task { appComponent.userPathsGenerator.startupInfoPath.delete() }.fail { e ->
                log.error("Error removing startup info: {}", e.message, e)
            }
        }
    }

    fun updateNetworkStatus(isAvailable: Boolean) {
        //ignore dup updates
        if (isAvailable == isNetworkAvailable)
            return

        isNetworkAvailable = isAvailable
        log.info("Network is available: {}", isAvailable)

        networkAvailableSubject.onNext(isAvailable)

        if (!isAvailable) {
            //airplane mode tells us the network is unavailable but doesn't actually disconnect us; we still receive
            //data but can't send it (at least on the emu)
            userComponent?.relayClientManager?.disconnect()
            return
        }

        connectToRelay()

        fetchOfflineMessages()
    }

    fun createUserSession(userLoginData: UserLoginData, accountInfo: AccountInfo): UserComponent {
        if (userComponent != null)
            error("UserComponent already loaded")

        log.info("Creating user session")

        val userComponent = appComponent.plus(UserModule(userLoginData, accountInfo))
        this.userComponent = userComponent

        return userComponent
    }

    /**
     * Handles initialization steps requiring IO, etc.
     *
     * Until this completes, do NOT use anything in the UserComponent.
     */
    private fun backgroundInitialization(userComponent: UserComponent, authToken: String?, password: String, rememberMe: Boolean): Promise<Unit, Exception> {
        val userPaths = userComponent.userPaths
        val accountInfo = userComponent.accountInfo
        val persistenceManager = userComponent.sqlitePersistenceManager
        val userLoginData = userComponent.userLoginData
        val keyVault = userLoginData.keyVault
        val userId = userLoginData.userId
        val sessionDataPath = appComponent.userPathsGenerator.getPaths(userId).sessionDataPath
        val startupInfoPath = appComponent.userPathsGenerator.startupInfoPath

        //we could break this up into parts and emit progress events between stages
        return task {
            createUserPaths(userPaths)
        } bind {
            if (authToken != null) {
                val cachedData = SessionData(authToken)
                JsonSessionDataPersistenceManager(sessionDataPath, keyVault.localDataEncryptionKey, keyVault.localDataEncryptionParams).store(cachedData)
            }
            else
                Promise.ofSuccess(Unit)
        } bind {
            storeAccountData(keyVault, accountInfo)
        } bind {
            if (rememberMe) {
                val startupInfo = StartupInfo(userId, password)
                JsonStartupInfoPersistenceManager(startupInfoPath).store(startupInfo)
            }
            else
                Promise.ofSuccess<Unit, Exception>(Unit)
        } bind {
            persistenceManager.initAsync()
        }
    }

    /** called after a successful user session has been created to finish initializing components. */
    private fun finalizeInit(userComponent: UserComponent, keyRegenCount: Int) {
        initializeUserSession(userComponent)

        //dagger lazily initializes all components, so we need to force creation
        userComponent.notifierService.init()

        userSessionAvailableSubject.onNext(true)

        userComponent.contactSyncManager.fullSync()
        //TODO rerun this a second time after a certain amount of time to pick up any messages that get added between this fetch
        fetchOfflineMessages()
        schedulePreKeyUpload(keyRegenCount)

        emitLoginEvent(LoggedIn(userComponent.accountInfo))
    }

    //TODO queue if offline/etc
    fun fetchOfflineMessages() {
        userComponent?.offlineMessageManager?.fetch()
    }

    /** Subscribe to events, connect to relay (if network available). */
    private fun initializeUserSession(userComponent: UserComponent) {
        userComponent.relayClientManager.onlineStatus.subscribe {
            onRelayStatusChange(it)
        }

        userComponent.relayClientManager.events.subscribe { handleRelayClientEvent(it) }

        if (!isNetworkAvailable) {
            log.info("Network unavailable, not connecting to relay")
            return
        }

        connectToRelay()
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
                fetchOfflineMessages()
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
        userComponent.authTokenManager.invalidateToken()
    }

    private fun reconnectToRelay() {
        if (!isNetworkAvailable)
            return

        if (reconnectionTimerSubscription != null) {
            log.warn("Relay reconnection already queued, ignoring reconnect request")
            return
        }

        val userComponent = this.userComponent
        if (userComponent == null) {
            log.error("Attempt to queue reconnect when not logged in")
            return
        }

        reconnectionTimerSubscription = reconnectionTimer.next().subscribe {
            reconnectionTimerSubscription = null

            val currentUserComponent = this.userComponent
            if (currentUserComponent != null) {
                if (userComponent.accountInfo == currentUserComponent.accountInfo)
                    connectToRelay()
                else
                    log.warn("Ignoring reconnect from previously logged in account")
            }
            else
                log.warn("No longer logged in, aborting reconnect")
        }

        log.info("Attempting to reconnect to relay in {}s", reconnectionTimer.waitTimeSeconds)
    }

    private fun onRelayStatusChange(isOnline: Boolean) {
        relayAvailableSubject.onNext(isOnline)
    }

    /**
     * Connect to the relay server.
     */
    private fun connectToRelay() {
        if (!isNetworkAvailable)
            return

        val userComponent = this.userComponent
        if (userComponent == null) {
            log.warn("User session has already been terminated")
            return
        }

        val username = userComponent.userLoginData.address
        //FIXME
        userComponent.authTokenManager.runUi { authToken ->
            val userCredentials = UserCredentials(username, authToken.string)
            userComponent.relayClientManager.connect(userCredentials)
            Promise.ofSuccess<Unit, Exception>(Unit)
        }
    }

    private fun deinitializeUserSession(userComponent: UserComponent) {
        userComponent.sqlitePersistenceManager.shutdown()
        userComponent.relayClientManager.disconnect()
    }

    /** Returns true if a session was present, false otherwise. */
    fun destroyUserSession(): Boolean {
        val userComponent = this.userComponent ?: return false

        contactsSyncSub?.unsubscribe()
        contactsSyncSub = null

        newTokenSyncSub?.unsubscribe()
        newTokenSyncSub = null

        log.info("Destroying user session")

        val sub = reconnectionTimerSubscription
        if (sub != null) {
            log.debug("Cancelling relay reconnection timer")
            sub.unsubscribe()
            reconnectionTimerSubscription = null
        }

        //notify listeners before tearing down session
        userSessionAvailableSubject.onNext(false)

        //TODO shutdown stuff; probably should return a promise
        deinitializeUserSession(userComponent)

        this.userComponent = null

        return true
    }

    fun shutdown() {
        destroyUserSession()
    }

    private fun storeAccountData(keyVault: KeyVault, accountInfo: AccountInfo): Promise<Unit, Exception> {
        val userComponent = this.userComponent ?: error("No user session")

        //TODO combine?
        userComponent.accountInfoPersistenceManager.store(accountInfo) fail { e ->
            log.error("Unable to store account info: {}", e.message, e)
        }

        return userComponent.keyVaultPersistenceManager.store(keyVault) fail { e ->
            log.error("Unable to store keyvault: {}", e.message, e)
        }
    }

    private fun initializationComplete() {
        isInitialized = true
        onInitListeners.forEach { it(this) }
        onInitListeners.clear()
    }

    /** Adds a function to be called once the app has finished initializing. */
    fun addOnInitListener(body: (KeyTapApplication) -> Unit) {
        if (isInitialized)
            body(this)
        else
            onInitListeners.add(body)
    }
}
