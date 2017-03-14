package io.slychat.messenger.services

import com.fasterxml.jackson.core.JsonParseException
import io.slychat.messenger.core.*
import io.slychat.messenger.core.crypto.KeyVault
import io.slychat.messenger.core.http.api.authentication.DeviceInfo
import io.slychat.messenger.core.kovenant.recover
import io.slychat.messenger.core.persistence.*
import io.slychat.messenger.core.relay.*
import io.slychat.messenger.core.sentry.*
import io.slychat.messenger.services.LoginEvent.*
import io.slychat.messenger.services.auth.AuthApiResponseException
import io.slychat.messenger.services.auth.AuthResult
import io.slychat.messenger.services.di.*
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
import rx.subscriptions.CompositeSubscription
import java.io.IOException
import java.util.*
import java.util.concurrent.ArrayBlockingQueue
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

    var isFirstRun: Boolean = false
        private set

    internal var isInitialized = false
    private val onInitListeners = ArrayList<(SlyApplication) -> Unit>()

    private var isAutoLoginComplete = false
    private val onAutoLoginListeners = ArrayList<(SlyApplication) -> Unit>()

    //the following observables never complete or error and are valid for the lifetime of the application
    //only changes in value are emitted from these
    private val networkAvailableSubject = BehaviorSubject.create(false)
    val networkAvailable: Observable<Boolean>
        get() = networkAvailableSubject

    private val relayAvailableSubject = BehaviorSubject.create(false)
    val relayAvailable: Observable<Boolean>
        get() = relayAvailableSubject

    private val userSessionAvailableSubject = BehaviorSubject.create(null as UserComponent?)
    val userSessionAvailable: Observable<UserComponent?>
        get() = userSessionAvailableSubject

    private val userComponentSubscriptions = CompositeSubscription()

    private val loginEventsSubject = BehaviorSubject.create<LoginEvent>()
    val loginEvents: Observable<LoginEvent>
        get() = loginEventsSubject

    var loginState: LoginState = LoginState.LOGGED_OUT
        private set

    lateinit var installationData: InstallationData

    private lateinit var keepAliveObservable: Observable<Long>
    private var keepAliveTimerSub: Subscription? = null

    private var connectingToRelay = false
    //if we're disconnecting and we get a connect request during that time, we force a reconnect on disconnect
    private var wantRelayReconnect = false

    private var bugReportSubmitter: ReportSubmitter<ByteArray>? = null

    var isInBackground: Boolean = true
        set(value) {
            field = value

            if (value)
                disconnectFromRelay()
            else
                connectToRelay()
        }

        get() = field

    /** Only called directly when used for testing. */
    internal fun init(applicationComponent: ApplicationComponent, doAutoLogin: Boolean = false) {
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            log.error("Uncaught exception in thread <{}>: {}", thread.name, throwable.message, throwable)
        }

        appComponent = applicationComponent

        bugReportSubmitter = initSentry(appComponent)

        initializeApplicationServices()

        initInstallationData()

        val interval = SlyBuildConfig.relayKeepAliveIntervalMs
        keepAliveObservable = Observable.interval(interval, interval, TimeUnit.MILLISECONDS, appComponent.rxScheduler)

        //must be done after bugReportSubmitter is set, so that it can receive the initial network status
        appComponent.networkStatus.subscribe { updateNetworkStatus(it) }

        appComponent.uiVisibility.subscribe { Sentry.setIsUiVisible(it) }

        //android can fire these events multiple time in succession (eg: when google account sync is occuring)
        //so we clamp down the number of events we process
        appComponent.platformContacts.contactsUpdated
            .debounce(4000, TimeUnit.MILLISECONDS)
            .observeOn(appComponent.rxScheduler)
            .subscribe { onPlatformContactsUpdated() }

        appComponent.appConfigService.init() fail {
            log.warn("Unable to read app config file: {}", it.message, it)
        } alwaysUi {
            initializationComplete(doAutoLogin)
        }

        applicationComponent.pushNotificationsManager.init()

        applicationComponent.versionChecker.init()
    }

    private fun initSentry(applicationComponent: ApplicationComponent): ReportSubmitter<ByteArray>? {
        val reporter = applicationComponent.reportSubmitter ?: return null

        log.debug("Initializing bug reporter")

        val thread = Thread({
            try {
                reporter.run()
            }
            catch (t: Throwable) {
                log.error("ReportSubmitterImpl terminated with error: {}", t.message, t)
            }
        })

        thread.isDaemon = true
        thread.name = "Bug Report Submitter"
        thread.priority = Thread.MIN_PRIORITY

        thread.start()

        Sentry.setReportSubmitter(reporter)

        return reporter
    }

    /** Starts background initialization; use addOnInitListener to be notified when app has finished initializing. Once finalized, will trigger auto-login. */
    fun init(platformModule: PlatformModule) {
        log.info("Operating System: {} {}", currentOs.name, currentOs.version)

        val appComponent = DaggerApplicationComponent.builder()
            .platformModule(platformModule)
            .applicationModule(ApplicationModule(this))
            .build()

        init(appComponent, true)
    }

    private fun initializationComplete(doAutoLogin: Boolean) {
        log.info("Initialization complete")
        isInitialized = true
        onInitListeners.forEach { it(this) }
        onInitListeners.clear()

        if (doAutoLogin)
            autoLogin()
    }

    /**
     * Will call the given listener once the app config file has been read.
     * If the file has already been read, the listener is called immediately.
     */
    fun addOnInitListener(listener: (SlyApplication) -> Unit) {
        if (isInitialized)
            listener(this)
        else
            onInitListeners.add(listener)
    }

    private fun onPlatformContactsUpdated() {
        val userComponent = userComponent ?: return

        log.debug("Platform contacts updated")

        userComponent.contactsService.doFindPlatformContacts()
    }

    //XXX this is kinda bad since we block on the main thread, but it's only done once during init anyways
    fun initInstallationData() {
        val persistenceManager = appComponent.installationDataPersistenceManager

        val maybeInstallationData = try {
            persistenceManager.retrieve().get()
        }
        //all of jackson's json deserialization errors are subclasses of IOException
        catch (e: IOException) {
            log.error("Installation data is corrupted: {}", e.message, e)
            null
        }

        isFirstRun = maybeInstallationData == null
        appComponent.uiClientInfoService.isFirstRun = isFirstRun

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

        val localAccountDirectory = appComponent.localAccountDirectory

        val startupInfoPersistenceManager = localAccountDirectory.getStartupInfoPersistenceManager(installationData.startupInfoKey)

        //XXX this is kinda inefficient, since we already have the userid, then we fetch the email to pass to the normal login functions
        startupInfoPersistenceManager.retrieve() map { startupInfo ->
            if (startupInfo != null) {
                val accountInfo = localAccountDirectory.findAccountFor(startupInfo.lastLoggedInAccount)
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
            log.warn("Unable to read startup info: {}", e.message, e)
            emitLoginEvent(LoggedOut())
            autoLoginComplete()
        }
    }

    fun resetLoginEvent() {
        emitLoginEvent(LoginEvent.LoggedOut())
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
            val accountParams = response.accountLocalInfo
            val sessionData = response.sessionData
            val address = SlyAddress(accountInfo.id, accountInfo.deviceId)

            val userLoginData = UserData(address, response.remotePasswordHash)

            val userComponent = createUserSession(userLoginData, keyVault, accountInfo, accountParams)

            val authTokenManager = userComponent.authTokenManager
            if (sessionData.authToken != null)
                authTokenManager.setToken(sessionData.authToken)
            else
                authTokenManager.invalidateToken()

            //until this finishes, nothing in the UserComponent should be touched
            backgroundInitialization(userComponent, response, password, rememberMe) mapUi {
                finalizeInitialization(userComponent, accountInfo, sessionData)
            }
        } failUi { e ->
            //incase session initialization failed we need to clean up the user session here
            destroyUserSession()

            val isError = (e !is AuthApiResponseException) && isNotNetworkError(e)
            log.condError(isError, "Login failed: {}", e.message, e)

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
        val sessionDataManager = userComponent.sessionDataManager

        log.info("Updating on-disk session data")

        if (authToken == null) {
            //need to reconnect, since the token is no longer valid
            disconnectFromRelay()

            return
        }

        sessionDataManager.updateAuthToken(authToken)

        connectToRelay()
    }

    private fun onClockDiffUpdate(diff: Long) {
        val userComponent = userComponent ?: return

        userComponent.sessionDataManager.updateClockDifference(diff)
    }

    /**
     * Log out of the current session. Meant to be called when the user explicitly requests to terminate a session.
     *
     * Emits LoggedOut.
     */
    fun logout() {
        val startupInfoPersistenceManager = appComponent.localAccountDirectory.getStartupInfoPersistenceManager(installationData.startupInfoKey)
        val sessionDataManager = userComponent?.sessionDataManager

        if (destroyUserSession()) {
            emitLoginEvent(LoggedOut())
            task {
                startupInfoPersistenceManager.delete()
                sessionDataManager?.delete()
            }.fail { e ->
                log.error("Error removing startup info: {}", e.message, e)
            }
        }
    }

    private fun updateNetworkStatus(isAvailable: Boolean) {
        //ignore dup updates
        if (isAvailable == isNetworkAvailable)
            return

        Sentry.setIsNetworkAvailable(isAvailable)

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

        checkPreKeys()
    }

    fun createUserSession(userLoginData: UserData, keyVault: KeyVault, accountInfo: AccountInfo, accountLocalInfo: AccountLocalInfo): UserComponent {
        if (userComponent != null)
            error("UserComponent already loaded")

        log.info("Creating user session")

        val userComponent = appComponent.plus(UserModule(userLoginData, keyVault, accountInfo, accountLocalInfo))
        this.userComponent = userComponent

        Sentry.setUserAddress(userComponent.userLoginData.address)

        return userComponent
    }

    /**
     * Handles initialization steps requiring IO, etc.
     *
     * Until this completes, do NOT use anything in the UserComponent.
     */
    private fun backgroundInitialization(
        userComponent: UserComponent,
        authResult: AuthResult,
        password: String,
        rememberMe: Boolean
    ): Promise<Unit, Exception> {
        val persistenceManager = userComponent.persistenceManager
        val userConfigService = userComponent.userConfigService
        val userData = userComponent.userLoginData
        val keyVault = userComponent.keyVault
        val userId = userData.userId

        val localAccountDirectory = appComponent.localAccountDirectory
        val startupInfoPersistenceManager = localAccountDirectory.getStartupInfoPersistenceManager(installationData.startupInfoKey)
        val sessionDataManager = userComponent.sessionDataManager
        val accountParamsManager = userComponent.accountLocalInfoManager

        val sessionData = authResult.sessionData
        val accountInfo = authResult.accountInfo
        val otherDevices = authResult.otherDevices

        //we could break this up into parts and emit progress events between stages
        return task {
            localAccountDirectory.createUserDirectories(userId)
        } bind {
            accountParamsManager.update(authResult.accountLocalInfo)
        } bind {
            sessionDataManager.update(sessionData)
        } bind {
            storeAccountData(keyVault, accountInfo)
        } bind {
            if (rememberMe) {
                val startupInfo = StartupInfo(userId, password)
                startupInfoPersistenceManager.store(startupInfo)
            }
            else
                Promise.ofSuccess<Unit, Exception>(Unit)
        } bind {
            persistenceManager.initAsync()
        } bind {
            //FIXME this can be performed in parallel
            userConfigService.init() recover {
                log.warn("Unable to read user config file: {}", it.message, it)
            }
        } mapUi {
            //need to do this here so we can use messageCipherService afterwards
            startUserComponents(userComponent)
        } bind {
            //TODO should probably only really run this on initial account data creation
            //we need to be present in our address book to create signal sessions
            if (otherDevices != null) {
                val publicKey = keyVault.fingerprint
                val selfInfo = ContactInfo(
                    userId,
                    accountInfo.email,
                    accountInfo.name,
                    //we don't wanna be visible by default
                    AllowedMessageLevel.GROUP_ONLY,
                    publicKey
                )

                userComponent.contactsService.addSelf(selfInfo)
            }
            else
                Promise.ofSuccess(Unit)
        } bind {
            if (otherDevices != null) {
                userComponent.messageCipherService.updateSelfDevices(otherDevices) bindUi {
                    val deviceInfo = DeviceInfo(accountInfo.deviceId, installationData.registrationId)
                    userComponent.messengerService.broadcastNewDevice(deviceInfo)
                }
            }
            else
                Promise.ofSuccess(Unit)
        }
    }

    private fun startUserComponents(userComponent: UserComponent) {
        //dagger lazily initializes all components, so we need to force creation
        userComponent.notifierService.init()
        userComponent.messageProcessor.init()
        userComponent.messengerService.init()
        userComponent.messageCipherService.init()
        userComponent.mutualContactNotifier.init()
        userComponent.conversationWatcher.init()
        userComponent.addressBookSyncWatcher.init()
        userComponent.messageExpirationWatcher.init()
        userComponent.messageReadWatcher.init()
        userComponent.messageDeletionWatcher.init()
        userComponent.groupEventLoggerWatcher.init()
        userComponent.transferManager.init()
        userComponent.storageService.init()
    }

    private fun shutdownUserComponents(userComponent: UserComponent) {
        userComponent.groupEventLoggerWatcher.shutdown()
        userComponent.messageDeletionWatcher.shutdown()
        userComponent.messageReadWatcher.shutdown()
        userComponent.messageExpirationWatcher.shutdown()
        userComponent.addressBookSyncWatcher.shutdown()
        userComponent.conversationWatcher.shutdown()
        userComponent.mutualContactNotifier.shutdown()
        userComponent.messageCipherService.shutdown(false)
        userComponent.messengerService.shutdown()
        userComponent.messageProcessor.shutdown()
        userComponent.contactsService.shutdown()
        userComponent.offlineMessageManager.shutdown()
        userComponent.preKeyManager.shutdown()
        userComponent.persistenceManager.shutdown()
        userComponent.notifierService.shutdown()
        userComponent.transferManager.shutdown()
        userComponent.storageService.shutdown()
    }

    //should come up with something better...
    private fun forceAddressBookSync() {
        userComponent?.apply { addressBookOperationManager.withCurrentSyncJobNoScheduler { doPull() } }
    }

    /** called after a successful user session has been created to finish initializing components. */
    private fun finalizeInitialization(userComponent: UserComponent, accountInfo: AccountInfo, sessionData: SessionData) {
        log.info("Finalizing user initialization")

        userComponentSubscriptions.add(userComponent.authTokenManager.newToken.subscribe {
            onNewToken(it)
        })

        //we don't care about receiving the event here
        userComponent.relayClock.setDifference(sessionData.relayClockDifference)

        userComponentSubscriptions.add(userComponent.relayClock.clockDiffUpdates.subscribe {
            onClockDiffUpdate(it)
        })

        initializeUserSession(userComponent)

        userSessionAvailableSubject.onNext(userComponent)

        forceAddressBookSync()

        //TODO rerun this a second time after a certain amount of time to pick up any messages that get added between this fetch
        fetchOfflineMessages()

        checkPreKeys()

        val publicKey = userComponent.keyVault.fingerprint

        emitLoginEvent(LoggedIn(accountInfo, publicKey))
    }

    fun fetchOfflineMessages() {
        userComponent?.offlineMessageManager?.fetch()
    }

    private fun checkPreKeys() {
        userComponent?.preKeyManager?.checkForUpload()
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
        log.debug("Starting relay keep alive")
        keepAliveTimerSub = keepAliveObservable.subscribe {
            userComponent?.relayClientManager?.sendPing()
        }
    }

    private fun stopRelayKeepAlive() {
        if (keepAliveTimerSub != null)
            log.debug("Stopping relay keep alive")

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
                log.info("Relay authentication failed")
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

        when (relayClientManager.state) {
            //just mark this so we'll attempt to reconnect later
            RelayClientState.DISCONNECTING ->
                wantRelayReconnect = true

            RelayClientState.DISCONNECTED -> {
                connectingToRelay = true

                userComponent.authTokenManager.mapUi { userCredentials ->
                    relayClientManager.connect(userCredentials)
                } fail { e ->
                    log.error("Unable to connect to relay: {}", e.message, e)
                } alwaysUi {
                    connectingToRelay = false
                }
            }

            //do nothing
            else -> {}
        }
    }

    private fun disconnectFromRelay() {
        val userComponent = this.userComponent ?: return
        stopRelayKeepAlive()
        userComponent.relayClientManager.disconnect()
    }

    private fun deinitializeUserSession(userComponent: UserComponent) {
        shutdownUserComponents(userComponent)
        disconnectFromRelay()
    }

    /** Returns true if a session was present, false otherwise. */
    fun destroyUserSession(): Boolean {
        val userComponent = this.userComponent ?: return false

        userComponentSubscriptions.clear()

        log.info("Destroying user session")

        val sub = reconnectionTimerSubscription
        if (sub != null) {
            log.debug("Cancelling relay reconnection timer")
            sub.unsubscribe()
            reconnectionTimerSubscription = null
        }

        this.userComponent = null

        //notify listeners before tearing down session
        userSessionAvailableSubject.onNext(null)

        //TODO shutdown stuff; probably should return a promise
        deinitializeUserSession(userComponent)

        Sentry.setUserAddress(null)

        return true
    }

    fun shutdown() {
        destroyUserSession()
    }

    private fun storeAccountData(keyVault: KeyVault, accountInfo: AccountInfo): Promise<Unit, Exception> {
        val userComponent = this.userComponent ?: error("No user session")

        //TODO combine?
        userComponent.accountInfoManager.update(accountInfo) fail { e ->
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
