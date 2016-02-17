package com.vfpowertech.keytap.android

import android.app.Application
import com.almworks.sqlite4java.SQLite
import nl.komponents.kovenant.android.androidUiDispatcher
import nl.komponents.kovenant.ui.KovenantUi

class App : Application() {
    override fun onCreate() {
        super.onCreate()

        SQLite.loadLibrary()
        KovenantUi.uiContext {
            dispatcher = androidUiDispatcher()
        }
    }
}