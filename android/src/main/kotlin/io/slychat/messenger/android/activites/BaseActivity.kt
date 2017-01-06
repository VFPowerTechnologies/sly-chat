package io.slychat.messenger.android.activites

import android.os.Bundle
import android.support.v7.app.AppCompatActivity
import io.slychat.messenger.android.AndroidApp
import io.slychat.messenger.android.R
import io.slychat.messenger.android.activites.services.impl.SettingsServiceImpl

open class BaseActivity: AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        val app = AndroidApp.get(this)
        val currentTheme = app.appComponent.appConfigService.appearanceTheme

        if (currentTheme == SettingsServiceImpl.lightTheme) {
            setTheme(R.style.SlyThemeLight)
        }

        super.onCreate(savedInstanceState)
    }
}