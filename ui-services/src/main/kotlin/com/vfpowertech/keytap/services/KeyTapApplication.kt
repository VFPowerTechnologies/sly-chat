package com.vfpowertech.keytap.services

import com.fasterxml.jackson.core.JsonParseException
import com.google.i18n.phonenumbers.PhoneNumberUtil
import com.google.i18n.phonenumbers.PhoneNumberUtil.PhoneNumberFormat
import com.vfpowertech.keytap.core.KeyTapAddress
import com.vfpowertech.keytap.core.crypto.KeyVault
import com.vfpowertech.keytap.core.div
import com.vfpowertech.keytap.core.http.api.contacts.*
import com.vfpowertech.keytap.core.http.api.offline.OfflineMessagesAsyncClient
import com.vfpowertech.keytap.core.http.api.offline.OfflineMessagesClearRequest
import com.vfpowertech.keytap.core.http.api.offline.OfflineMessagesGetRequest
import com.vfpowertech.keytap.core.http.api.prekeys.PreKeyStoreAsyncClient
import com.vfpowertech.keytap.core.http.api.prekeys.preKeyStorageRequestFromGeneratedPreKeys
import com.vfpowertech.keytap.core.persistence.*
import com.vfpowertech.keytap.core.persistence.json.JsonAccountInfoPersistenceManager
import com.vfpowertech.keytap.core.persistence.json.JsonInstallationDataPersistenceManager
import com.vfpowertech.keytap.core.persistence.json.JsonSessionDataPersistenceManager
import com.vfpowertech.keytap.core.persistence.json.JsonStartupInfoPersistenceManager
import com.vfpowertech.keytap.core.relay.*
import com.vfpowertech.keytap.services.crypto.deserializeEncryptedMessage
import com.vfpowertech.keytap.services.di.*
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

    private val loginEventsSubject = BehaviorSubject.create<LoginEvent>()
    val loginEvents: Observable<LoginEvent> = loginEventsSubject

    var loginState: LoginState = LoginState.LOGGED_OUT
        private set

    lateinit var installationData: InstallationData

    private var fetchingOfflineMessages = false

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

        appComponent.platformContacts.contactsUpdated.subscribe {
            onPlatformContactsUpdated()
        }
    }

    private fun onPlatformContactsUpdated() {
        val userComponent = userComponent ?: return
        userComponent.userLoginData.authToken ?: return

        //TODO don't run if other sync is running
        //TODO queue if offline
        if (isNetworkAvailable) {
            contactListSyncingSubject.onNext(true)
            syncLocalContacts(userComponent) fail { e ->
                log.error("Local contact sync failed: {}", e.message, e)
            } alwaysUi {
                contactListSyncingSubject.onNext(false)
            }
        }
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

    fun schedulePreKeyUpload(keyRegenCount: Int) {
        if (pushingPreKeys || keyRegenCount <= 0)
            return

        val userComponent = this.userComponent
        if (userComponent == null) {
            log.warn("schedulePreKeyUpload called without a user session")
            return
        }

        log.info("Requested to generate {} new prekeys", keyRegenCount)

        val keyVault = userComponent.userLoginData.keyVault
        val authToken = userComponent.userLoginData.authToken
        if (authToken == null) {
            log.error("Unable to push prekeys, no auth token available")
            return
        }

        pushingPreKeys = true

        //TODO need to mark whether or not a range has been pushed to the server or not
        //if the push fails, we should delete the batch?
        //TODO nfi what to do if server response fails
        userComponent.preKeyManager.generate() bind { r ->
            val (generatedPreKeys, lastResortPreKey) = r
            val request = preKeyStorageRequestFromGeneratedPreKeys(authToken, installationData.registrationId, keyVault, generatedPreKeys, lastResortPreKey)
            PreKeyStoreAsyncClient(appComponent.serverUrls.API_SERVER).store(request)
        } successUi { response ->
            pushingPreKeys = false

            if (!response.isSuccess)
                log.error("PreKey push failed: {}", response.errorMessage)
            else
                log.info("Pushed prekeys to server")
        } failUi { e ->
            pushingPreKeys = false

            log.error("PreKey push failed: {}", e.message, e)
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

        //TODO this only works if an email is given; we need to somehow keep a list of phone numbers -> emails somewhere
        //maybe just search every account dir available and find a number that way? kinda rough but works

        //if the unlock fails, we try remotely; this can occur if the password was changed remotely from another device
        appComponent.authenticationService.auth(username, password, installationData.registrationId) bindUi { response ->
            val keyVault = response.keyVault
            //TODO need to put the username in the login response if the user used their phone number
            val address = KeyTapAddress(response.accountInfo.id, response.accountInfo.deviceId)
            val userLoginData = UserLoginData(address, keyVault, response.authToken)
            val userComponent = createUserSession(userLoginData, response.accountInfo)

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

        //do nothing if we're not logged in
        val userComponent = this.userComponent ?: return

        connectToRelay(userComponent)

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

        //TODO reschedule this if it's not?
        if (isNetworkAvailable) {
            //TODO maybe just have this update before doing the actual updates?
            //the issue with the syncing is that removing contacts will remove the convos, so we can't have the user access
            //a convo that'll be removed during sync
            contactListSyncingSubject.onNext(true)
            syncRemoteContactsList(userComponent) bind {
                syncLocalContacts(userComponent)
            } fail { e ->
                log.error("Contacts syncing failed: {}", e.message, e)
            } alwaysUi {
                contactListSyncingSubject.onNext(false)
            }

            //TODO rerun this a second time after a certain amount of time to pick up any messages that get added between this fetch
            fetchOfflineMessages()
            schedulePreKeyUpload(keyRegenCount)
        }

        emitLoginEvent(LoggedIn(userComponent.accountInfo))
    }

    /** Syncs the local contact list with the remote contact list. */
    private fun syncRemoteContactsList(userComponent: UserComponent): Promise<Unit, Exception> {
        log.debug("Beginning remote contact list sync")

        val client = ContactListAsyncClient(appComponent.serverUrls.API_SERVER)

        val keyVault = userComponent.userLoginData.keyVault
        val authToken = userComponent.userLoginData.authToken
        if (authToken == null) {
            log.debug("authToken is null, aborting remote contacts sync")
            return Promise.ofFail(RuntimeException("Null authToken"))
        }

        val contactsPersistenceManager = userComponent.contactsPersistenceManager

        return client.getContacts(GetContactsRequest(authToken)) bind { response ->
            val emails = decryptRemoteContactEntries(keyVault, response.contacts)
            contactsPersistenceManager.getDiff(emails) bind { diff ->
                log.debug("New contacts: {}", diff.newContacts)
                log.debug("Removed contacts: {}", diff.removedContacts)

                val contactsClient = ContactAsyncClient(appComponent.serverUrls.API_SERVER)
                val request = FetchContactInfoByIdRequest(authToken, diff.newContacts.toList())
                contactsClient.fetchContactInfoByEmail(request) bind { response ->
                    contactsPersistenceManager.applyDiff(response.contacts, diff.removedContacts.toList())
                }
            }
        }
    }

    /** Attempts to find any registered users matching the user's local contacts. */
    private fun syncLocalContacts(userComponent: UserComponent): Promise<Unit, Exception> {
        val authToken = userComponent.userLoginData.authToken
        if (authToken == null) {
            log.debug("authToken is null, aborting local contacts sync")
            return Promise.ofFail(RuntimeException("Null authToken"))
        }

        val keyVault = userComponent.userLoginData.keyVault

        val phoneNumberUtil = PhoneNumberUtil.getInstance()
        val phoneNumber = phoneNumberUtil.parse("+${userComponent.accountInfo.phoneNumber}", null)
        val defaultRegion = phoneNumberUtil.getRegionCodeForCountryCode(phoneNumber.countryCode)

        return appComponent.platformContacts.fetchContacts() map { contacts ->
            val phoneNumberUtil = PhoneNumberUtil.getInstance()

            val updated = contacts.map { contact ->
                val phoneNumbers = contact.phoneNumbers.map { parsePhoneNumber(it, defaultRegion) }.filter { it != null }.map { phoneNumberUtil.format(it, PhoneNumberFormat.E164).substring(1) }
                contact.copy(phoneNumbers = phoneNumbers)
            }

            log.debug("Platform contacts: {}", updated)

            updated
        } bind { contacts ->
            userComponent.contactsPersistenceManager.findMissing(contacts)
        } bind { missingContacts ->
            log.debug("Missing local contacts:", missingContacts)
            val client = ContactAsyncClient(appComponent.serverUrls.API_SERVER)
            client.findLocalContacts(FindLocalContactsRequest(authToken, missingContacts))
        } bind { foundContacts ->
            log.debug("Found local contacts: {}", foundContacts)

            val client = ContactListAsyncClient(appComponent.serverUrls.API_SERVER)
            val remoteContactEntries = encryptRemoteContactEntries(keyVault, foundContacts.contacts.map { it.id })
            val request = AddContactsRequest(authToken, remoteContactEntries)

            client.addContacts(request) bind {
                userComponent.contactsPersistenceManager.addAll(foundContacts.contacts.map { ContactInfo(it.id, it.email, it.name, it.phoneNumber, it.publicKey) })
            }
        } fail { e ->
            log.error("Local contacts sync failed: {}", e.message, e)
        }
    }

    //TODO queue if offline/etc
    fun fetchOfflineMessages() {
        if (fetchingOfflineMessages)
            return

        fetchingOfflineMessages = true

        val authToken = userComponent?.userLoginData?.authToken ?: return

        log.info("Fetching offline messages")

        val offlineMessagesClient = OfflineMessagesAsyncClient(appComponent.serverUrls.API_SERVER)
        offlineMessagesClient.get(OfflineMessagesGetRequest(authToken)) bindUi { response ->
            if (response.messages.isNotEmpty()) {
                val messengerService = userComponent?.messengerService ?: throw RuntimeException("No longer logged in")

                //TODO move this elsewhere?
                val offlineMessages = response.messages.map { m ->
                    val encryptedMessage = deserializeEncryptedMessage(m.serializedMessage)
                    OfflineMessage(m.from, m.timestamp, encryptedMessage)
                }

                messengerService.addOfflineMessages(offlineMessages) bind {
                    offlineMessagesClient.clear(OfflineMessagesClearRequest(authToken, response.range))
                }
            }
            else
                Promise.ofSuccess(Unit)
        } fail { e ->
            log.error("Unable to fetch offline messages: {}", e.message, e)
        } alwaysUi {
            fetchingOfflineMessages = false
        }
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
        val data = userComponent.userLoginData
        data.authToken = null
        val remotePasswordHash = data.keyVault.remotePasswordHash

        val sessionDataPersistenceManager = userComponent.sessionDataPersistenceManager

        appComponent.authenticationService.refreshAuthToken(userComponent.accountInfo, installationData.registrationId, remotePasswordHash) bind { response ->
            log.info("Got new auth token")
            sessionDataPersistenceManager.store(SessionData(response.authToken)) map { response }
        } successUi { response ->
            data.authToken = response.authToken

            schedulePreKeyUpload(response.keyRegenCount)

            reconnectToRelay()
        } fail { e ->
            log.error("Unable to refresh auth token", e)
        }
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
                    connectToRelay(currentUserComponent)
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

    /** Fetches auth token if none is given, then connects to the relay. */
    private fun connectToRelay(userComponent: UserComponent) {
        if (!isNetworkAvailable)
            return

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

    /** Returns true if a session was present, false otherwise. */
    fun destroyUserSession(): Boolean {
        val userComponent = this.userComponent ?: return false

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
