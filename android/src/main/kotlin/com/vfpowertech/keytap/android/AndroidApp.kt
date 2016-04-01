package com.vfpowertech.keytap.android

import android.app.Application
import android.content.Context
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.preference.PreferenceManager
import android.support.v4.content.ContextCompat
import com.almworks.sqlite4java.SQLite
import com.google.android.gms.iid.InstanceID
import com.vfpowertech.keytap.android.services.AndroidPlatformContacts
import com.vfpowertech.keytap.android.services.AndroidUIPlatformInfoService
import com.vfpowertech.keytap.core.BuildConfig
import com.vfpowertech.keytap.core.http.api.gcm.GcmAsyncClient
import com.vfpowertech.keytap.core.http.api.gcm.RegisterRequest
import com.vfpowertech.keytap.core.http.api.gcm.RegisterResponse
import com.vfpowertech.keytap.core.http.api.gcm.UnregisterRequest
import com.vfpowertech.keytap.services.KeyTapApplication
import com.vfpowertech.keytap.services.LoginState
import com.vfpowertech.keytap.services.di.ApplicationComponent
import com.vfpowertech.keytap.services.di.PlatformModule
import com.vfpowertech.keytap.services.ui.createAppDirectories
import nl.komponents.kovenant.Promise
import nl.komponents.kovenant.android.androidUiDispatcher
import nl.komponents.kovenant.ui.KovenantUi
import nl.komponents.kovenant.ui.successUi
import org.slf4j.LoggerFactory
import rx.android.schedulers.AndroidSchedulers
import java.util.*

class AndroidApp : Application() {
    val app: KeyTapApplication = KeyTapApplication()

    private val log = LoggerFactory.getLogger(javaClass)

    lateinit var notificationService: AndroidNotificationService

    /** Points to the current activity, if one is set. Used to request permissions from various services. */
    var currentActivity: MainActivity? = null
        set(value) {
            field = value
            val notifierService = app.userComponent?.notifierService
            if (notifierService != null)
                notifierService.isUiVisible = value != null
        }

    lateinit var appComponent: ApplicationComponent
        private set

    override fun onCreate() {
        super.onCreate()

        SQLite.loadLibrary()
        KovenantUi.uiContext {
            dispatcher = androidUiDispatcher()
        }

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
            AndroidSchedulers.mainThread()
        )

        app.init(platformModule)
        appComponent = app.appComponent

        val filter = IntentFilter(ConnectivityManager.CONNECTIVITY_ACTION)
        val networkReceiver = NetworkStatusReceiver()
        registerReceiver(networkReceiver, filter)

        app.userSessionAvailable.subscribe {
            if (it == true)
                onUserSessionCreated()
            else
                onUserSessionDestroyed()
        }

        app.autoLogin()
    }

    fun isFocusedActivity(): Boolean = currentActivity != null

    //this serves to also handle any issues where somehow the settings get out of sync and multiple users
    //have tokenSent=true
    private fun resetTokenSentForUsers() {
        val sharedPrefs = PreferenceManager.getDefaultSharedPreferences(this)

        val usernames = sharedPrefs.getStringSet(AndroidPreferences.tokenUserList, setOf())

        val editor = sharedPrefs.edit()
        usernames.forEach { username ->
            editor.putBoolean(AndroidPreferences.getTokenSentToServer(username), false)
        }
        editor.apply()
    }

    fun onGCMTokenRefreshRequired() {
        refreshGCMToken()
    }

    private fun refreshGCMToken() {
        val userComponent = app.userComponent ?: return

        //make sure only the current user has token sent set to true
        resetTokenSentForUsers()

        //TODO queue if network isn't active
        if (app.isNetworkAvailable)
            gcmFetchToken(this, userComponent.userLoginData.username).successUi { onGCMTokenRefresh(it.username, it.token) }
    }

    private fun pushGcmTokenToServer(authToken: String, token: String): Promise<RegisterResponse, Exception> {
        val serverUrl = app.appComponent.serverUrls.API_SERVER
        val request = RegisterRequest(authToken, token, app.installationData.installationId)
        return GcmAsyncClient(serverUrl).register(request)
    }

    fun onGCMTokenRefresh(username: String, token: String) {
        val userComponent = app.userComponent ?: return

        //if we've logged out since, do nothing (the token'll be refreshed again on next login)
        if (userComponent.userLoginData.username != username)
            return

        log.debug("Received GCM token for {}: {}", username, token)

        val authToken = userComponent.userLoginData.authToken
        if (authToken == null) {
            log.warn("Unable to push GCM token to server, no auth token available")
            return
        }

        pushGcmTokenToServer(authToken, token) successUi { response ->
            if (response.isSuccess) {
                log.info("GCM token successfully registered with server")

                val sharedPrefs = PreferenceManager.getDefaultSharedPreferences(this)
                val usernames = HashSet(sharedPrefs.getStringSet(AndroidPreferences.tokenUserList, HashSet()))
                val editor = sharedPrefs.edit()
                editor.putBoolean(AndroidPreferences.getTokenSentToServer(username), true)
                usernames.add(username)
                editor.putStringSet(AndroidPreferences.tokenUserList, usernames)
                editor.apply()
            }
            //TODO
            else {
                log.error("Error registering token: {}", response.errorMessage)
            }
        } fail { e ->
            log.error("Error registering token: {}", e.message, e)
        }
    }

    fun onGCMMessage(account: String, offlineMessageInfoList: Array<OfflineMessageInfo>) {
        //it's possible we might receive a message targetting a diff account that was previously logged in
        app.addOnInitListener { app ->
            //the app might not be finished logging in yet
            //if we have auto-login, this will at least be LOGGING_IN (since login is called before we get here)

            //in this case we just delete the token, as every new login reregisters a new token anyways
            //so if we have no auto-login but we're still receiving gcm messages we haven't deleted the existing token
            if (app.loginState == LoginState.LOGGED_OUT) {
                log.warn("Got a GCM message but no longer logged in; invalidating token")
                deleteGCMToken()
            }
            else if (app.loginState == LoginState.LOGGED_IN)
                app.fetchOfflineMessages()
        }
    }

    private fun onUserSessionCreated() {
        val userComponent = app.userComponent!!
        val username = userComponent.userLoginData.username

        userComponent.notifierService.isUiVisible = currentActivity != null

        val sharedPrefs = PreferenceManager.getDefaultSharedPreferences(this)
        val tokenSent = sharedPrefs.getBoolean(AndroidPreferences.getTokenSentToServer(username), false)
        if (!tokenSent)
            refreshGCMToken()
    }

    private fun deleteGCMToken() {
        gcmDeleteToken(this) fail { e ->
            if (e.message == InstanceID.ERROR_SERVICE_NOT_AVAILABLE || e.message == InstanceID.ERROR_TIMEOUT)
                log.error("InstanceID service unavailable: {}", e.message)
            else
                log.error("Unable to delete instance id due to instance error: {}", e.message, e)
        }
    }

    private fun onUserSessionDestroyed() {
        //occurs on startup when we first register for events
        val userComponent = app.userComponent ?: return

        val sharedPrefs = PreferenceManager.getDefaultSharedPreferences(this)
        sharedPrefs.edit().putBoolean(AndroidPreferences.getTokenSentToServer(userComponent.userLoginData.username), false).apply()

        //this is a best effort attempt at unregistering
        //even if this fails, the token'll be invalidated on the next login that registers one
        if (app.isNetworkAvailable) {
            deleteGCMToken()

            val authToken = userComponent.userLoginData.authToken
            if (authToken != null) {
                val serverUrl = app.appComponent.serverUrls.API_SERVER
                val request = UnregisterRequest(authToken, app.installationData.installationId)
                GcmAsyncClient(serverUrl).unregister(request) fail { e ->
                    log.error("Unable to unregister GCM token with server: {}", e.message, e)
                }
            }
            else
                log.warn("Not auth token available, unable to unregister remove token")
        }
    }

    fun updateNetworkStatus(isConnected: Boolean) {
        app.updateNetworkStatus(isConnected)
    }

    /** Use to request a runtime permission. If no activity is available, succeeds with false. */
    fun requestPermission(permission: String): Promise<Boolean, Exception> {
        if (ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED)
            return Promise.ofSuccess(true)

        val activity = currentActivity ?: return Promise.ofSuccess(false)

        return activity.requestPermission(permission)
    }

    companion object {
        fun get(context: Context): AndroidApp =
            context.applicationContext as AndroidApp
    }
}
