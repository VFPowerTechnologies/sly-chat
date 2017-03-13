package io.slychat.messenger.android

import android.Manifest
import android.app.Application
import android.content.Context
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.media.RingtoneManager
import android.net.ConnectivityManager
import android.os.StrictMode
import android.support.v4.content.ContextCompat
import com.almworks.sqlite4java.SQLite
import com.google.android.gms.common.ConnectionResult
import com.google.android.gms.common.GoogleApiAvailability
import com.google.android.gms.common.GooglePlayServicesNotAvailableException
import com.google.android.gms.common.GooglePlayServicesRepairableException
import com.google.android.gms.security.ProviderInstaller
import com.jaredrummler.android.device.DeviceName
import io.slychat.messenger.android.activites.BaseActivity
import io.slychat.messenger.android.services.AndroidPlatformContacts
import io.slychat.messenger.android.services.AndroidUILoadService
import io.slychat.messenger.android.services.AndroidUIPlatformInfoService
import io.slychat.messenger.android.services.AndroidUIPlatformService
import io.slychat.messenger.core.SlyAddress
import io.slychat.messenger.core.SlyBuildConfig
import io.slychat.messenger.core.UserId
import io.slychat.messenger.core.http.api.pushnotifications.PushNotificationService
import io.slychat.messenger.core.http.api.pushnotifications.PushNotificationsAsyncClient
import io.slychat.messenger.core.http.api.pushnotifications.PushNotificationsAsyncClientImpl
import io.slychat.messenger.core.persistence.AccountInfo
import io.slychat.messenger.core.pushnotifications.OfflineMessagesPushNotification
import io.slychat.messenger.services.*
import io.slychat.messenger.services.config.UserConfig
import io.slychat.messenger.services.di.ApplicationComponent
import io.slychat.messenger.services.di.PlatformModule
import io.slychat.messenger.services.di.UserComponent
import io.slychat.messenger.services.ui.UIConversation
import io.slychat.messenger.services.ui.createAppDirectories
import nl.komponents.kovenant.Promise
import nl.komponents.kovenant.android.androidUiDispatcher
import nl.komponents.kovenant.task
import nl.komponents.kovenant.ui.KovenantUi
import nl.komponents.kovenant.ui.successUi
import org.slf4j.LoggerFactory
import rx.Observable
import rx.android.schedulers.AndroidSchedulers
import rx.subjects.BehaviorSubject
import java.util.*

//TODO keep checking PushNotificationsClient.isRegistered on login? easy way to make sure stuff is still registered...
//maybe add to PushNotificationManager or something
class AndroidApp : Application() {
    companion object {
        fun get(context: Context): AndroidApp =
                context.applicationContext as AndroidApp

        /** Will never leak any exceptions. */
        private fun playServicesInit(context: Context): LoadError? {
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

        private fun playServicesInitAsync(context: Context): Promise<LoadError?, Exception> = task { playServicesInit(context) }
    }

    private var playServicesInitRunning = false
    private var playServicesInitComplete = false

    //TODO move this into settings
    private var noNotificationsOnLogout = false

    val app: SlyApplication = SlyApplication()

    private val onSuccessfulInitListeners = ArrayList<() -> Unit>()
    private var isInitialized = false
    private var wasSuccessfullyInitialized = false

    private val loadCompleteSubject = BehaviorSubject.create<LoadError?>()
    /** Fires once both GCM services and SlyApplication have completed initialization, in that order. */
    val loadComplete: Observable<LoadError?> = loadCompleteSubject.observeOn(AndroidSchedulers.mainThread())

    private val log = LoggerFactory.getLogger(javaClass)

    lateinit var notificationService: AndroidNotificationService

    private lateinit var pushNotificationsClient: PushNotificationsAsyncClient

    private val uiVisibility: BehaviorSubject<Boolean> = BehaviorSubject.create(false)
    private val networkStatus: BehaviorSubject<Boolean> = BehaviorSubject.create(false)

    var platformContactSyncOccured = true

    var currentActivity: BaseActivity? = null

    fun setCurrentActivity (activity: BaseActivity, visible: Boolean) {
        if (!visible && activity == currentActivity) {
            currentActivity = null
            uiVisibility.onNext(visible)
            return
        }

        if (visible) {
            currentActivity = activity
            uiVisibility.onNext(visible)
        }
    }

    var conversationCache: MutableMap<UserId, UIConversation> = mutableMapOf()

    var accountInfo : AccountInfo? = null
    var publicKey : String? = null

    lateinit var appComponent: ApplicationComponent
        private set

    override fun onCreate() {
        super.onCreate()

        SQLite.loadLibrary()
        KovenantUi.uiContext {
            dispatcher = androidUiDispatcher()
        }

        if (SlyBuildConfig.DEBUG) {
            val policy = StrictMode.ThreadPolicy.Builder()
                    .detectAll()
                    .penaltyLog()
                    .build()

            StrictMode.setThreadPolicy(policy)
        }

        runPlayServicesInit()
    }

    private fun init() {
        val platformInfo = AndroidPlatformInfo(this)
        createAppDirectories(platformInfo)

        notificationService = AndroidNotificationService(this)

        val defaultUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
        val defaultUserConfig = UserConfig().copy(
            notificationsSound = defaultUri.toString()
        )

        val platformModule = PlatformModule(
            AndroidUIPlatformInfoService(),
            SlyBuildConfig.ANDROID_SERVER_URLS,
            platformInfo,
            AndroidTelephonyService(this),
            AndroidUIWindowService(),
            AndroidPlatformContacts(this),
            notificationService,
            AndroidUIShareService(),
            AndroidUIPlatformService(),
            AndroidUILoadService(),
            uiVisibility,
            AndroidTokenFetcher(this),
            networkStatus,
            AndroidSchedulers.mainThread(),
            defaultUserConfig,
            PushNotificationService.GCM,
            AndroidFileAccess(this)
        )

        app.init(platformModule)
        notificationService.init(app.userSessionAvailable)

        appComponent = app.appComponent

        app.isInBackground = false

        pushNotificationsClient = PushNotificationsAsyncClientImpl(appComponent.serverUrls.API_SERVER, appComponent.slyHttpClientFactory)

        val packageManager = packageManager
        try {
            val info = packageManager.getPackageInfo("com.google.android.webview", 0)
            Sentry.setWebViewInfo(info.versionName)
        }
        catch (e: PackageManager.NameNotFoundException) {
            //do nothing
        }

        try {
            Sentry.setAndroidDeviceName(DeviceName.getDeviceName())
        }
        catch (e: Exception) {
            log.error("setAndroidDeviceInfo failed: {}", e.message, e)
        }

        Sentry.setBuildNumber(BuildConfig.VERSION_CODE.toString())

        val filter = IntentFilter(ConnectivityManager.CONNECTIVITY_ACTION)
        val networkReceiver = NetworkStatusReceiver()
        registerReceiver(networkReceiver, filter)

        app.userSessionAvailable.subscribe {
            if (it != null)
                onUserSessionCreated()
            else
                onUserSessionDestroyed()
        }

        //only do this once we've completed initialization (ie once AppConfigService is up)
        app.addOnInitListener {
            //no permissions required on android
            if (!appComponent.appConfigService.pushNotificationsPermRequested) {
                appComponent.appConfigService.withEditor {
                    pushNotificationsPermRequested = true
                }
            }

            appComponent.tokenFetchService.refresh()
        }
    }

    private fun runPlayServicesInit() {
        if (playServicesInitComplete || playServicesInitRunning)
            return

        playServicesInitRunning = true

        playServicesInitAsync(this) successUi { loadError ->
            playServicesInitRunning = false

            if (loadError == null) {
                playServicesInitComplete = true
                log.debug("Play Services init successful")
                init()
                app.addOnInitListener {
                    finishInitialization(null)
                }
            }
            else {
                if (loadError.cause != null)
                    log.error("Play Services init failure: {}: errorCode={}", loadError.cause.message, loadError.cause)
                else
                    log.error("Play Services init failure: {}: {}", loadError.type, loadError.errorCode)

                finishInitialization(loadError)
            }
        }
    }

    fun onGCMTokenRefreshRequired() {
        appComponent.tokenFetchService.refresh()
    }

    fun onGCMMessage(message: OfflineMessagesPushNotification) {
        val account = message.account

        //it's possible we might receive a message targetting a diff account that was previously logged in
        app.addOnAutoLoginListener { app ->
            //the app might not be finished logging in yet
            //if we have auto-login, this will at least be LOGGING_IN (since login is called before we get here)

            if (app.loginState == LoginState.LOGGED_OUT) {
                if (noNotificationsOnLogout) {
                    log.warn("Got a GCM message but no longer logged in; invalidating token")
                    //I guess? shouldn't really happen anymore since we retry unregistration
                    appComponent.pushNotificationsManager.unregister(account)
                }
                else
                    notificationService.showLoggedOutNotification(message)
            }
            else if (app.loginState == LoginState.LOGGED_IN) {
                //could maybe occur that we get older gcm messages for an account we were previously logged in as
                if (account == app.userComponent!!.userLoginData.address)
                    app.fetchOfflineMessages()
                else
                    log.warn("Got GCM message for different account ($account); ignoring")
            }
        }
    }

    private fun onUserSessionCreated() {
    }

    //TODO need SlyAddress; so need to add it to notification context
    fun stopReceivingNotifications(address: SlyAddress) {
        app.addOnInitListener {
            appComponent.pushNotificationsManager.unregister(address)
        }
    }

    private fun onUserSessionDestroyed() {
        //occurs on startup when we first register for events
        val userComponent = app.userComponent ?: return

        conversationCache = mutableMapOf()
        accountInfo = null
        publicKey = null

        if (noNotificationsOnLogout)
            appComponent.pushNotificationsManager.unregister(userComponent.userLoginData.address)
    }

    fun updateNetworkStatus(isConnected: Boolean) {
        networkStatus.onNext(isConnected)
    }

    /** Use to request a runtime permission. If no activity is available, succeeds with false. */
    fun requestPermission(permission: String): Promise<Boolean, Exception> {
        if (ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED)
            return Promise.ofSuccess(true)

        val activity = currentActivity
        if (permission == Manifest.permission.READ_CONTACTS && (activity is MainActivity || activity == null)) {
            platformContactSyncOccured = false
            return Promise.ofSuccess(false)
        }

        if (activity == null)
            return Promise.ofSuccess(false)

        return activity.requestPermission(permission)
    }

    private fun finishInitialization(loadError: LoadError?) {
        isInitialized = true
        loadCompleteSubject.onNext(loadError)

        if (loadError == null) {
            wasSuccessfullyInitialized = true

            onSuccessfulInitListeners.forEach { it() }
            onSuccessfulInitListeners.clear()
        }
    }

    /** Fires only if play services and SlyApplication have successfully completed initialization. Used by services. */
    fun addOnSuccessfulInitListener(listener: () -> Unit) {
        if (wasSuccessfullyInitialized) {
            listener()
        }
        else if (!isInitialized) {
            onSuccessfulInitListeners.add(listener)
        }
    }

    fun getUserComponent(): UserComponent {
        val userComponent = app.userComponent ?: throw Exception()

        return userComponent
    }

    fun dispatchEvent(type: String, page: PageType, extra: String) {
        val event = UIEvent.PageChange(page, extra)

        this.appComponent.uiEventService.dispatchEvent(event)
    }
}