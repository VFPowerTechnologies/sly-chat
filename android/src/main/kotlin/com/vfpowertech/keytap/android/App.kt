package com.vfpowertech.keytap.android

import android.app.Application
import android.content.Context
import com.almworks.sqlite4java.SQLite
import com.vfpowertech.keytap.android.services.AndroidPlatformInfoService
import com.vfpowertech.keytap.core.BuildConfig
import com.vfpowertech.keytap.services.KeyTapApplication
import com.vfpowertech.keytap.ui.services.KeyTapApplication
import com.vfpowertech.keytap.services.ui.createAppDirectories
import com.vfpowertech.keytap.services.di.ApplicationComponent
import com.vfpowertech.keytap.services.di.PlatformModule
import nl.komponents.kovenant.android.androidUiDispatcher
import nl.komponents.kovenant.ui.KovenantUi
import rx.android.schedulers.AndroidSchedulers

class App : Application() {
    private val app: KeyTapApplication = KeyTapApplication()

    lateinit var appComponent: ApplicationComponent

    override fun onCreate() {
        super.onCreate()

        SQLite.loadLibrary()
        KovenantUi.uiContext {
            dispatcher = androidUiDispatcher()
        }

        val platformInfo = AndroidPlatformInfo(this)
        createAppDirectories(platformInfo)

        val platformModule = PlatformModule(
            AndroidPlatformInfoService(),
            BuildConfig.ANDROID_SERVER_URLS,
            platformInfo,
            AndroidSchedulers.mainThread()
        )

        app.init(platformModule)
        appComponent = app.appComponent
    }

    companion object {
        fun get(context: Context): App =
            context.applicationContext as App
    }
}