package io.slychat.messenger.services

import com.fasterxml.jackson.core.JsonParseException
import io.slychat.messenger.core.*
import io.slychat.messenger.core.crypto.KeyVault
import io.slychat.messenger.core.persistence.AccountInfo
import io.slychat.messenger.core.persistence.InstallationData
import io.slychat.messenger.core.persistence.SessionData
import io.slychat.messenger.core.persistence.StartupInfo
import io.slychat.messenger.core.persistence.json.JsonAccountInfoPersistenceManager
import io.slychat.messenger.core.persistence.json.JsonInstallationDataPersistenceManager
import io.slychat.messenger.core.persistence.json.JsonSessionDataPersistenceManager
import io.slychat.messenger.core.persistence.json.JsonStartupInfoPersistenceManager
import io.slychat.messenger.core.relay.*
import io.slychat.messenger.core.sentry.ReportSubmitterCommunicator
import io.slychat.messenger.services.di.*
import io.slychat.messenger.services.LoginEvent.*
import nl.komponents.kovenant.Promise
import nl.komponents.kovenant.functional.bind
import nl.komponents.kovenant.functional.map
import nl.komponents.kovenant.task
import nl.komponents.kovenant.ui.alwaysUi
import nl.komponents.kovenant.ui.failUi
import nl.komponents.kovenant.ui.successUi
import org.slf4j.LoggerFactory
import rx.Observable
import rx.Subscription
import rx.subjects.BehaviorSubject
import java.util.*
import java.util.concurrent.TimeUnit

class SlyApplication {
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
    private val onInitListeners = ArrayList<(SlyApplication) -> Unit>()

    private var isAutoLoginComplete = false
    private val onAutoLoginListeners = ArrayList<(SlyApplication) -> Unit>()

    //the following observables never complete or error and are valid for the lifetime of the application
    //only changes in value are emitted from these
    private val networkAvailableSubject = BehaviorSubject.create(false)
    val networkAvailable: Observable<Boolean> = networkAvailableSubject

    private val relayAvailableSubject = BehaviorSubject.create(false)
    val relayAvailable: Observable<Boolean> = relayAvailableSubject

    private val userSessionAvailableSubject = BehaviorSubject.create(null as UserComponent?)
    val userSessionAvailable: Observable<UserComponent?> = userSessionAvailableSubject

    private var newTokenSyncSub: Subscription? = null

    private val loginEventsSubject = BehaviorSubject.create<LoginEvent>()
    val loginEvents: Observable<LoginEvent> = loginEventsSubject

    var loginState: LoginState = LoginState.LOGGED_OUT
        private set

    lateinit var installationData: InstallationData

    private lateinit var keepAliveObservable: Observable<Long>
    private var keepAliveTimerSub: Subscription? = null

    private var connectingToRelay = false
    //if we're disconnecting and we get a connect request during that time, we force a reconnect on disconnect
    private var wantRelayReconnect = false

    private var bugReportSubmitter: ReportSubmitterCommunicator<ByteArray>? = null

    var isInBackground: Boolean = true
        set(value) {
            field = value

            if (value)
                disconnectFromRelay()
            else
                connectToRelay()
        }

        get() = field


    /** Starts background initialization; use addOnInitListener to be notified when app has finished initializing. Once finalized, will trigger auto-login. */
    fun init(platformModule: PlatformModule) {
        log.info("Operating System: {} {}", currentOs.name, currentOs.version)

        appComponent = DaggerApplicationComponent.builder()
            .platformModule(platformModule)
            .applicationModule(ApplicationModule(this))
            .build()

        initializeApplicationServices()

        initInstallationData()

        Sentry.setInstallationId(installationData.installationId)

        val interval = BuildConfig.relayKeepAliveIntervalMs
        keepAliveObservable = Observable.interval(interval, interval, TimeUnit.MILLISECONDS, appComponent.rxScheduler)

        bugReportSubmitter = initSentry(appComponent)

        //android can fire these events multiple time in succession (eg: when google account sync is occuring)
        //so we clamp down the number of events we process
        appComponent.platformContacts.contactsUpdated
            .debounce(4000, TimeUnit.MILLISECONDS)
            .observeOn(appComponent.rxScheduler)
            .subscribe { onPlatformContactsUpdated() }

        appComponent.appConfigService.init() successUi {
            initializationComplete()
        }
    }

    private fun initializationComplete() {
        log.info("Initialization complete")
        isInitialized = true
        onInitListeners.forEach { it(this) }
        onInitListeners.clear()

        autoLogin()
    }

    fun addOnInitListener(listener: (SlyApplication) -> Unit) {
        if (isInitialized)
            listener(this)
        else
            onInitListeners.add(listener)
    }

    private fun onPlatformContactsUpdated() {
        val userComponent = userComponent ?: return

        log.debug("Platform contacts updated")

        userComponent.contactsService.doLocalSync()
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
    private fun autoLogin() {
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
            autoLoginComplete()
        } failUi { e ->
            log.error("Unable to read startup info: {}", e.message, e)
            emitLoginEvent(LoggedOut())
            autoLoginComplete()
        }
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

        //if the unlock fails, we try remotely; this can occur if the password was changed remotely from another device
        appComponent.authenticationService.auth(username, password, installationData.registrationId) bindUi { response ->
            val keyVault = response.keyVault

            val accountInfo = response.accountInfo
            val address = SlyAddress(accountInfo.id, accountInfo.deviceId)
            val userLoginData = UserData(address, keyVault)
            val userComponent = createUserSession(userLoginData)

            val authTokenManager = userComponent.authTokenManager
            if (response.authToken != null)
                authTokenManager.setToken(response.authToken)
            else
                authTokenManager.invalidateToken()

            //until this finishes, nothing in the UserComponent should be touched
            backgroundInitialization(userComponent, response.authToken, password, rememberMe, accountInfo) mapUi {
                finalizeInit(userComponent, accountInfo)
            }
        } failUi { e ->
            //incase session initialization failed we need to clean up the user session here
            destroyUserSession()

            log.warn("Login failed: {}", e.message, e)

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
        val sessionDataPersistenceManager = userComponent.sessionDataPersistenceManager

        log.info("Updating on-disk session data")

        if (authToken == null) {
            //XXX it's unlikely but possible this might run AFTER a new token comes in and gets written to disk
            //depending on load and scheduler behavior
            sessionDataPersistenceManager.delete() fail { e ->
                log.error("Error during session data file removal: {}", e.message, e)
            }

            //need to reconnect, since the token is no longer valid
            disconnectFromRelay()

            return
        }

        sessionDataPersistenceManager.store(SessionData(authToken)) fail { e ->
            log.error("Unable to write session data to disk: {}", e.message, e)
        }

        connectToRelay()

        userComponent.preKeyManager.checkForUpload()
    }

    /**
     * Log out of the current session. Meant to be called when the user explicitly requests to terminate a session.
     *
     * Emits LoggedOut.
     */
    fun logout() {
        val sessionDataPath = userComponent?.userPaths?.sessionDataPath

        if (destroyUserSession()) {
            emitLoginEvent(LoggedOut())
            task {
                appComponent.userPathsGenerator.startupInfoPath.delete()
                sessionDataPath?.delete()
            }.fail { e ->
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

        bugReportSubmitter?.updateNetworkStatus(isAvailable)

        if (!isAvailable) {
            //airplane mode tells us the network is unavailable but doesn't actually disconnect us; we still receive
            //data but can't send it (at least on the emu)
            disconnectFromRelay()
            return
        }

        connectToRelay()

        forceAddressBookSync()

        fetchOfflineMessages()
    }

    fun createUserSession(userLoginData: UserData): UserComponent {
        if (userComponent != null)
            error("UserComponent already loaded")

        log.info("Creating user session")

        val userComponent = appComponent.plus(UserModule(userLoginData))
        this.userComponent = userComponent

        Sentry.setUserAddress(userComponent.userLoginData.address)

        return userComponent
    }

    /**
     * Handles initialization steps requiring IO, etc.
     *
     * Until this completes, do NOT use anything in the UserComponent.
     */
    private fun backgroundInitialization(userComponent: UserComponent, authToken: AuthToken?, password: String, rememberMe: Boolean, accountInfo: AccountInfo): Promise<Unit, Exception> {
        val userPaths = userComponent.userPaths
        val persistenceManager = userComponent.sqlitePersistenceManager
        val userConfigService = userComponent.configService
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
        } bind {
            //FIXME this can be performed in parallel
            userConfigService.init()
        }
    }

    private fun startUserComponents(userComponent: UserComponent) {
        //dagger lazily initializes all components, so we need to force creation
        userComponent.notifierService.init()
        userComponent.messengerService.init()
        userComponent.messageCipherService.start()
    }

    private fun shutdownUserComponents(userComponent: UserComponent) {
        userComponent.messageCipherService.shutdown(false)
        userComponent.messengerService.shutdown()
        userComponent.contactsService.shutdown()
        userComponent.offlineMessageManager.shutdown()
        userComponent.preKeyManager.shutdown()
        userComponent.sqlitePersistenceManager.shutdown()
    }

    //should come up with something better...
    private fun forceAddressBookSync() {
        userComponent?.apply { addressBookOperationManager.withCurrentSyncJobNoScheduler { doRemoteSync() } }
    }

    /** called after a successful user session has been created to finish initializing components. */
    private fun finalizeInit(userComponent: UserComponent, accountInfo: AccountInfo) {
        newTokenSyncSub = userComponent.authTokenManager.newToken.subscribe {
            onNewToken(it)
        }

        initializeUserSession(userComponent)

        startUserComponents(userComponent)

        userSessionAvailableSubject.onNext(userComponent)

        forceAddressBookSync()

        //TODO rerun this a second time after a certain amount of time to pick up any messages that get added between this fetch
        fetchOfflineMessages()

        val publicKey = userComponent.userLoginData.keyVault.fingerprint

        emitLoginEvent(LoggedIn(accountInfo, publicKey))
    }

    fun fetchOfflineMessages() {
        userComponent?.offlineMessageManager?.fetch()
    }

    /** Subscribe to events, connect to relay (if network available). */
    private fun initializeUserSession(userComponent: UserComponent) {
        //this is here because this writes session data to disk, so we need to make sure the directories are set up
        //prior to this being called
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

    private fun startRelayKeepAlive() {
        keepAliveTimerSub = keepAliveObservable.subscribe {
            userComponent?.relayClientManager?.sendPing()
        }
    }

    private fun stopRelayKeepAlive() {
        keepAliveTimerSub?.unsubscribe()
        keepAliveTimerSub = null
    }

    //for things like cert verification failure, this has like 3 layers of "General SSLEngine problem" which isn't very useful
    private fun getRelayConnectionErrorMessage(exception: Throwable): String? {
        var current: Throwable? = exception
        while (current != null && current.message?.contains("General SSLEngine problem") ?: false)
            current = current.cause

        return current?.message ?: exception.message
    }

    private fun handleRelayClientEvent(event: RelayClientEvent) {
        when (event) {
            is ConnectionEstablished -> {
                wantRelayReconnect = false
                reconnectionTimer.reset()
            }

            is ConnectionLost -> {
                stopRelayKeepAlive()

                if (!event.wasRequested || wantRelayReconnect) reconnectToRelay()
            }

            is ConnectionFailure -> {
                log.warn("Connection to relay failed: {}", getRelayConnectionErrorMessage(event.error))
                reconnectToRelay()
            }

            is AuthenticationSuccessful -> {
                startRelayKeepAlive()

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
        wantRelayReconnect = false

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
                if (userComponent.userLoginData.address == currentUserComponent.userLoginData.address)
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

        if (connectingToRelay)
            return

        if (isInBackground)
            return

        val userComponent = this.userComponent ?: return

        val relayClientManager = userComponent.relayClientManager

        if (relayClientManager.state == RelayClientState.DISCONNECTING)
            wantRelayReconnect = true

        if (relayClientManager.isOnline)
            return

        connectingToRelay = true

        userComponent.authTokenManager.mapUi { userCredentials ->
            relayClientManager.connect(userCredentials)
        } fail { e ->
            log.error("Unable to retrieve auth token for relay connection: {}", e.message, e)
        } alwaysUi {
            //after connect() is called, relayClientManager.isOnline will be true
            connectingToRelay = false
        }
    }

    private fun disconnectFromRelay() {
        val userComponent = this.userComponent ?: return
        userComponent.relayClientManager.disconnect()
    }

    private fun deinitializeUserSession(userComponent: UserComponent) {
        shutdownUserComponents(userComponent)
        disconnectFromRelay()
    }

    /** Returns true if a session was present, false otherwise. */
    fun destroyUserSession(): Boolean {
        val userComponent = this.userComponent ?: return false

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
        userSessionAvailableSubject.onNext(null)

        //TODO shutdown stuff; probably should return a promise
        deinitializeUserSession(userComponent)

        this.userComponent = null

        Sentry.setUserAddress(null)

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

    private fun autoLoginComplete() {
        isAutoLoginComplete = true
        onAutoLoginListeners.forEach { it(this) }
        onAutoLoginListeners.clear()
    }

    /** Adds a function to be called once the app has finished attempting to auto-login. */
    fun addOnAutoLoginListener(body: (SlyApplication) -> Unit) {
        if (isAutoLoginComplete)
            body(this)
        else
            onAutoLoginListeners.add(body)
    }
}
