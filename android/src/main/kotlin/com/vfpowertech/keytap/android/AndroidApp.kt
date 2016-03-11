package com.vfpowertech.keytap.android

import android.app.Application
import android.content.Context
import android.content.IntentFilter
import android.net.ConnectivityManager
import com.almworks.sqlite4java.SQLite
import com.vfpowertech.keytap.android.services.AndroidUIPlatformInfoService
import com.vfpowertech.keytap.core.BuildConfig
import com.vfpowertech.keytap.services.KeyTapApplication
import com.vfpowertech.keytap.services.di.ApplicationComponent
import com.vfpowertech.keytap.services.di.PlatformModule
import com.vfpowertech.keytap.services.ui.createAppDirectories
import nl.komponents.kovenant.Promise
import nl.komponents.kovenant.android.androidUiDispatcher
import nl.komponents.kovenant.ui.KovenantUi
import rx.android.schedulers.AndroidSchedulers

class AndroidApp : Application() {
    val app: KeyTapApplication = KeyTapApplication()

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
            AndroidSchedulers.mainThread()
        )

        app.init(platformModule)
        appComponent = app.appComponent

        val filter = IntentFilter(ConnectivityManager.CONNECTIVITY_ACTION)
        val networkReceiver = NetworkStatusReceiver()
        registerReceiver(networkReceiver, filter)
    }

    fun updateNetworkStatus(isConnected: Boolean) {
        app.updateNetworkStatus(isConnected)
    }

    /** Use to request a runtime permission. If no activity is available, succeeds with false. */
    fun requestPermission(permission: String): Promise<Boolean, Exception> {
        val activity = currentActivity ?: return Promise.ofSuccess(false)

        return activity.requestPermission(permission)
    }

    companion object {
        fun get(context: Context): AndroidApp =
            context.applicationContext as AndroidApp
    }
}