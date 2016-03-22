package com.vfpowertech.keytap.android

import android.app.Application
import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.media.RingtoneManager
import android.net.ConnectivityManager
import android.preference.PreferenceManager
import android.support.v4.content.ContextCompat
import com.almworks.sqlite4java.SQLite
import com.vfpowertech.keytap.android.services.AndroidPlatformContacts
import com.vfpowertech.keytap.android.services.AndroidUIPlatformInfoService
import com.vfpowertech.keytap.core.BuildConfig
import com.vfpowertech.keytap.core.http.api.gcm.GcmAsyncClient
import com.vfpowertech.keytap.core.http.api.gcm.RegisterRequest
import com.vfpowertech.keytap.core.http.api.gcm.RegisterResponse
import com.vfpowertech.keytap.services.KeyTapApplication
import com.vfpowertech.keytap.services.di.ApplicationComponent
import com.vfpowertech.keytap.services.di.PlatformModule
import com.vfpowertech.keytap.services.di.UserComponent
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

    /** Points to the current activity, if one is set. Used to request permissions from various services. */
    var currentActivity: MainActivity? = null

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

        val platformModule = PlatformModule(
            AndroidUIPlatformInfoService(),
            BuildConfig.ANDROID_SERVER_URLS,
            platformInfo,
            AndroidTelephonyService(this),
            AndroidWindowService(this),
            AndroidPlatformContacts(this),
            AndroidSchedulers.mainThread()
        )

        app.init(platformModule)
        appComponent = app.appComponent

        val filter = IntentFilter(ConnectivityManager.CONNECTIVITY_ACTION)
        val networkReceiver = NetworkStatusReceiver()
        registerReceiver(networkReceiver, filter)

        app.userSessionAvailable.subscribe { if (it == true) onUserSessionCreation() }
    }

    fun isFocusedActivity(): Boolean = currentActivity != null

    fun onGCMTokenRefreshRequired() {
        //we need to make sure every user attached to this install gets their token refreshed on next login
        val sharedPrefs = PreferenceManager.getDefaultSharedPreferences(this)

        val usernames = sharedPrefs.getStringSet(AndroidPreferences.tokenUserList, setOf())

        val editor = sharedPrefs.edit()
        usernames.forEach { username ->
            editor.putBoolean(AndroidPreferences.getTokenSentToServer(username), false)
        }
        editor.apply()

        refreshGCMToken()
    }

    private fun refreshGCMToken() {
        val userComponent = app.userComponent ?: return
        gcmFetchToken(this, userComponent.userLoginData.username).successUi { onGCMTokenRefresh(it.username, it.token) }
    }

    private fun pushGcmTokenToServer(authToken: String, token: String): Promise<RegisterResponse, Exception> {
        val serverUrl = app.appComponent.serverUrls.API_SERVER
        //TODO installationId
        val request = RegisterRequest(authToken, token, "0")
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
        //TODO if the app is closed then the user isn't logged in, since right now the ui handles the auto-login
        //showing multiple account notifications is annoying since we have to give them different ids
        //so right now we just show a notification anyways
        //val userComponent = app.userComponent ?: return

        //TODO fetch offline messages if logged in

        //if we have offline messages, fetching them'll show the notifications if required
        if (isFocusedActivity()) {
            println("Focused, not sending notification")
            return
        }

        //TODO should handle users not being in contacts list, etc
        val contentText = if (offlineMessageInfoList.size == 1) {
            val info = offlineMessageInfoList[0]
            val count = info.pendingCount
            //TODO fix this when we add i18n
            val plural = if (count > 1) "s" else ""
            "You have $count new message$plural from ${info.name}"
        }
        else {
            val totalMessageCount = offlineMessageInfoList.fold(0) { acc, info -> acc + info.pendingCount }
            "You have $totalMessageCount new messages"
        }

        val intent = Intent(this, MainActivity::class.java)
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
        intent.addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
        val pendingIntent = PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_ONE_SHOT)

        val soundUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)

        val notification = Notification.Builder(this)
            .setContentTitle("New message available")
            .setContentText(contentText)
            .setSmallIcon(R.drawable.ic_mms_black_24dp)
            .setSound(soundUri)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()

        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(NOTIFICATION_ID_NEW_MESSAGES, notification)
    }

    //TODO only if logged in
    fun cancelPendingNotifications() {
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.cancel(AndroidApp.NOTIFICATION_ID_NEW_MESSAGES)
    }

    private fun onUserSessionCreation() {
        val userComponent = app.userComponent!!
        val username = userComponent.userLoginData.username

        val sharedPrefs = PreferenceManager.getDefaultSharedPreferences(this)
        val tokenSent = sharedPrefs.getBoolean(AndroidPreferences.getTokenSentToServer(username), false)
        if (!tokenSent)
            refreshGCMToken()
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
        val NOTIFICATION_ID_NEW_MESSAGES: Int = 0

        fun get(context: Context): AndroidApp =
            context.applicationContext as AndroidApp
    }
}
