package com.vfpowertech.keytap.android

import android.content.Context
import com.vfpowertech.keytap.services.ui.UIWindowService

class AndroidWindowService(private val context: Context) : UIWindowService {
    override fun minimize() {
        val androidApp = AndroidApp.get(context)

        val activity = androidApp.currentActivity ?: return
        activity.moveTaskToBack(true)
    }
}