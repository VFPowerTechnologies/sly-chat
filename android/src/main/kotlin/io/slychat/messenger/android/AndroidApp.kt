package io.slychat.messenger.android

import android.app.Application
import android.content.Context
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.support.v4.content.ContextCompat
import com.almworks.sqlite4java.SQLite
import com.google.android.gms.common.ConnectionResult
import com.google.android.gms.common.GoogleApiAvailability
import com.google.android.gms.common.GooglePlayServicesNotAvailableException
import com.google.android.gms.common.GooglePlayServicesRepairableException
import com.google.android.gms.iid.InstanceID
import com.google.android.gms.security.ProviderInstaller
import io.slychat.messenger.android.services.AndroidPlatformContacts
import io.slychat.messenger.android.services.AndroidUILoadService
import io.slychat.messenger.android.services.AndroidUIPlatformInfoService
import io.slychat.messenger.android.services.AndroidUIPlatformService
import io.slychat.messenger.core.BuildConfig
import io.slychat.messenger.core.SlyAddress
import io.slychat.messenger.core.UserCredentials
import io.slychat.messenger.core.UserId
import io.slychat.messenger.core.http.api.gcm.*
import io.slychat.messenger.services.LoginState
import io.slychat.messenger.services.Sentry
import io.slychat.messenger.services.SlyApplication
import io.slychat.messenger.services.di.ApplicationComponent
import io.slychat.messenger.services.di.PlatformModule
import io.slychat.messenger.services.ui.createAppDirectories
import nl.komponents.kovenant.Promise
import nl.komponents.kovenant.android.androidUiDispatcher
import nl.komponents.kovenant.task
import nl.komponents.kovenant.ui.KovenantUi
import nl.komponents.kovenant.ui.failUi
import nl.komponents.kovenant.ui.successUi
import org.slf4j.LoggerFactory
import rx.Observable
import rx.android.schedulers.AndroidSchedulers
import rx.subjects.BehaviorSubject
import java.util.*

enum class LoadErrorType {
    SSL_PROVIDER_INSTALLATION_FAILURE,
    NO_PLAY_SERVICES,
    UNKNOWN
}

//only one of errorCode or cause will be provided
data class LoadError(
    val type: LoadErrorType,
    //0 if not provided
    val errorCode: Int,
    val cause: Throwable?
)

/** Will never leak any exceptions. */
fun gcmInit(context: Context): LoadError? {
    try {
        val apiAvailability = GoogleApiAvailability.getInstance()

        val resultCode = apiAvailability.isGooglePlayServicesAvailable(context)

        if (resultCode != ConnectionResult.SUCCESS)
            return LoadError(LoadErrorType.NO_PLAY_SERVICES, resultCode, null)

        try {
            ProviderInstaller.installIfNeeded(context)
        }
        catch (e: GooglePlayServicesRepairableException) {
            return LoadError(LoadErrorType.SSL_PROVIDER_INSTALLATION_FAILURE, e.connectionStatusCode, null)

        }
        catch (e: GooglePlayServicesNotAvailableException) {
            //shouldn't happen?
            return LoadError(LoadErrorType.NO_PLAY_SERVICES, e.errorCode, null)
        }

        return null
    }
    catch (t: Throwable) {
        return LoadError(LoadErrorType.UNKNOWN, 0, t)
    }
}

fun gcmInitAsync(context: Context): Promise<LoadError?, Exception> = task { gcmInit(context) }

class AndroidApp : Application() {
    private var gcmInitRunning = false
    private var gcmInitComplete = false

    private var gcmRegistering = false

    //TODO move this into settings
    private var noNotificationsOnLogout = false

    val app: SlyApplication = SlyApplication()

    //if AndroidUILoadService.loadComplete is called while we're paused (eg: during the permissions dialog)
    private var queuedLoadComplete = false

    private val loadCompleteSubject = BehaviorSubject.create<LoadError>()
    val loadComplete: Observable<LoadError> = loadCompleteSubject.observeOn(AndroidSchedulers.mainThread())

    private val log = LoggerFactory.getLogger(javaClass)

    lateinit var notificationService: AndroidNotificationService

    //set to true once we've made this request once since startup
    private var hasCheckedGcmTokenStatus = false

    /** Points to the current activity, if one is set. Used to request permissions from various services. */
    var currentActivity: MainActivity? = null
        set(value) {
            field = value

            val notifierService = app.userComponent?.notifierService
            if (notifierService != null)
                notifierService.isUiVisible = value != null

            if (queuedLoadComplete)
                queuedLoadComplete = hideSplashImage() == false

            app.isInBackground = value == null
        }

    lateinit var appComponent: ApplicationComponent
        private set

    override fun onCreate() {
        super.onCreate()

        SQLite.loadLibrary()
        KovenantUi.uiContext {
            dispatcher = androidUiDispatcher()
        }

        runGcmInit()
    }

    private fun init() {
        val platformInfo = AndroidPlatformInfo(this)
        createAppDirectories(platformInfo)

        notificationService = AndroidNotificationService(this)

        val platformModule = PlatformModule(
            AndroidUIPlatformInfoService(),
            BuildConfig.ANDROID_SERVER_URLS,
            platformInfo,
            AndroidTelephonyService(this),
            AndroidWindowService(this),
            AndroidPlatformContacts(this),
            notificationService,
            AndroidUIPlatformService(this),
            AndroidUILoadService(this),
            AndroidSchedulers.mainThread()
        )

        app.init(platformModule)
        appComponent = app.appComponent

        val packageManager = packageManager
        try {
            val info = packageManager.getPackageInfo("com.google.android.webview", 0)
            Sentry.setWebViewInfo(info.versionName)
        }
        catch (e: PackageManager.NameNotFoundException) {
            //do nothing
        }

        val filter = IntentFilter(ConnectivityManager.CONNECTIVITY_ACTION)
        val networkReceiver = NetworkStatusReceiver()
        registerReceiver(networkReceiver, filter)

        app.userSessionAvailable.subscribe {
            if (it == true)
                onUserSessionCreated()
            else
                onUserSessionDestroyed()
        }
    }

    private fun runGcmInit() {
        if (gcmInitComplete || gcmInitRunning)
            return

        gcmInitRunning = true

        gcmInitAsync(this) successUi { loadError ->
            gcmInitRunning = false

            if (loadError == null) {
                gcmInitComplete = true
                log.debug("GCM init successful")
                init()
                app.autoLogin()
            }
            else {
                if (loadError.cause != null)
                    log.error("GCM init failure: {}: errorCode={}", loadError.cause.message, loadError.cause)
                else
                    log.error("GCM init failure: {}: {}", loadError.type, loadError.errorCode)
            }

            loadCompleteSubject.onNext(loadError)
        }
    }

    fun isFocusedActivity(): Boolean = currentActivity != null

    //this serves to also handle any issues where somehow the settings get out of sync and multiple users
    //have tokenSent=true
    private fun resetTokenSentForUsers() {
        val usernames = AndroidPreferences.getTokenUserList(this)

        AndroidPreferences.withEditor(this) {
            usernames.forEach { username ->
                val userId = UserId(username.toLong())
                setTokenSentToServer(userId, false)
            }
        }
    }

    fun onGCMTokenRefreshRequired() {
        refreshGCMToken(true)
    }

    private fun checkGCMTokenStatus() {
        if (!app.isNetworkAvailable || hasCheckedGcmTokenStatus)
            return

        val userComponent = app.userComponent ?: return

        val serverUrl = app.appComponent.serverUrls.API_SERVER
        val request = IsRegisteredRequest(app.installationData.installationId)
        userComponent.authTokenManager.bind { userCredentials ->
            GcmAsyncClient(serverUrl).isRegistered(userCredentials, request)
        } successUi { response ->
            log.info("GCM token is registered: {}", response.isRegistered)

            if (!response.isRegistered)
                refreshGCMToken(true)

            hasCheckedGcmTokenStatus = true
        } fail { e ->
            log.error("Unable to check GCM token status: {}", e.message, e)
        }
    }

    private fun refreshGCMToken(force: Boolean) {
        //we need to make sure we reset all tokens on force, since we may receive this when no user is logged in
        if (force)
            resetTokenSentForUsers()

        val userComponent = app.userComponent ?: return
        val userId = userComponent.userLoginData.userId

        if (gcmRegistering)
            return

        if (!force) {
            val tokenSent = AndroidPreferences.getTokenSentToServer(this, userId)
            if (tokenSent)
                return
        }

        AndroidPreferences.setIgnoreNotifications(this, false)

        //make sure only the current user has token sent set to true
        //don't need to do this twice, since if we're forcing we've already done this
        if (!force)
            resetTokenSentForUsers()

        if (app.isNetworkAvailable) {
            gcmRegistering = true

            gcmFetchToken(this, userComponent.userLoginData.address.id) successUi {
                gcmRegistering = false
                onGCMTokenRefresh(it.userId, it.token)
            } failUi { e ->
                log.error("GCM token registration failed: {}", e.message, e)
                gcmRegistering = false
            }
        }
    }

    private fun pushGcmTokenToServer(userCredentials: UserCredentials, token: String): Promise<RegisterResponse, Exception> {
        val serverUrl = app.appComponent.serverUrls.API_SERVER
        val request = RegisterRequest(token, app.installationData.installationId)
        return GcmAsyncClient(serverUrl).register(userCredentials, request)
    }

    fun onGCMTokenRefresh(userId: UserId, token: String) {
        val userComponent = app.userComponent ?: return

        //if we've logged out since, do nothing (the token'll be refreshed again on next login)
        if (userComponent.userLoginData.address.id != userId)
            return

        log.debug("Received GCM token for user {}: {}", userId.long, token)

        userComponent.authTokenManager.bind { userCredentials ->
            pushGcmTokenToServer(userCredentials, token)
        } successUi { response ->
            if (response.isSuccess) {
                log.info("GCM token successfully registered with server")

                val usernames = HashSet(AndroidPreferences.getTokenUserList(this))
                AndroidPreferences.withEditor(this) {
                    val username = userId.long.toString()
                    setTokenSentToServer(userId, true)
                    usernames.add(username)
                    setTokenUserList(usernames)
                }
            }
            //TODO
            else {
                log.error("Error registering token: {}", response.errorMessage)
            }
        } fail { e ->
            log.error("Error registering token: {}", e.message, e)
        }
    }

    fun onGCMMessage(account: SlyAddress, accountName: String, info: List<OfflineMessageInfo>) {
        //this can occur if we logged out when there was no network connection, or from a notification after we've
        //already requested the token to be deleted
        if (AndroidPreferences.getIgnoreNotifications(this)) {
            deleteGCMToken()
            return
        }

        //it's possible we might receive a message targetting a diff account that was previously logged in
        app.addOnInitListener { app ->
            //the app might not be finished logging in yet
            //if we have auto-login, this will at least be LOGGING_IN (since login is called before we get here)

            //in this case we just delete the token, as every new login reregisters a new token anyways
            //so if we have no auto-login but we're still receiving gcm messages we haven't deleted the existing token
            if (app.loginState == LoginState.LOGGED_OUT) {
                if (noNotificationsOnLogout) {
                    log.warn("Got a GCM message but no longer logged in; invalidating token")
                    deleteGCMToken()
                }
                else
                    notificationService.showLoggedOutNotification(accountName, info)
            }
            else if (app.loginState == LoginState.LOGGED_IN) {
                //could maybe occur that we get older gcm messages for an account we were previously logged in as
                if (account == app.userComponent!!.userLoginData.address)
                    app.fetchOfflineMessages()
                else
                    log.warn("Got GCM message for different account ($account);ignoring")
            }
        }
    }

    private fun onUserSessionCreated() {
        val userComponent = app.userComponent!!

        userComponent.notifierService.isUiVisible = currentActivity != null

        checkGCM()
    }

    fun stopReceivingNotifications() {
        deleteGCMToken()
    }

    private fun deleteGCMToken() {
        resetTokenSentForUsers()
        AndroidPreferences.setIgnoreNotifications(this, true)

        gcmDeleteToken(this) success {
            log.info("GCM token invalidated")
        } fail { e ->
            if (e.message == InstanceID.ERROR_SERVICE_NOT_AVAILABLE || e.message == InstanceID.ERROR_TIMEOUT)
                log.error("InstanceID service unavailable: {}", e.message)
            else
                log.error("Unable to delete instance id due to instance error: {}", e.message, e)
        }
    }

    private fun onUserSessionDestroyed() {
        hasCheckedGcmTokenStatus = false

        //occurs on startup when we first register for events
        val userComponent = app.userComponent ?: return

        if (noNotificationsOnLogout) {
            AndroidPreferences.setTokenSentToServer(this, userComponent.userLoginData.userId, false)

            AndroidPreferences.setIgnoreNotifications(this, true)

            //this is a best effort attempt at unregistering
            //even if this fails, the token'll be invalidated on the next login that registers one
            if (app.isNetworkAvailable) {
                deleteGCMToken()

                val serverUrl = app.appComponent.serverUrls.API_SERVER
                userComponent.authTokenManager.bind { userCredentials ->
                    val request = UnregisterRequest(app.installationData.installationId)
                    GcmAsyncClient(serverUrl).unregister(userCredentials, request)
                } fail { e ->
                    log.error("Unable to unregister GCM token with server: {}", e.message, e)
                }
            }
        }
    }

    private fun checkGCM() {
        if (!hasCheckedGcmTokenStatus)
            checkGCMTokenStatus()
        else
            refreshGCMToken(false)
    }

    fun updateNetworkStatus(isConnected: Boolean) {
        app.updateNetworkStatus(isConnected)

        if (isConnected) {
            checkGCM()
        }
    }

    /** Use to request a runtime permission. If no activity is available, succeeds with false. */
    fun requestPermission(permission: String): Promise<Boolean, Exception> {
        if (ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED)
            return Promise.ofSuccess(true)

        val activity = currentActivity ?: return Promise.ofSuccess(false)

        return activity.requestPermission(permission)
    }

    private fun hideSplashImage(): Boolean {
        val currentActivity = currentActivity as? MainActivity ?: return false

        currentActivity.hideSplashImage()
        return true
    }

    fun uiLoadCompleted() {
        queuedLoadComplete = hideSplashImage() == false
    }

    companion object {
        fun get(context: Context): AndroidApp =
            context.applicationContext as AndroidApp
    }
}
