package com.vfpowertech.keytap.android.services

import android.content.Context
import com.vfpowertech.keytap.android.AndroidApp
import com.vfpowertech.keytap.android.MainActivity
import com.vfpowertech.keytap.services.ui.UILoadService
import org.slf4j.LoggerFactory

class AndroidUILoadService(val context: Context) : UILoadService {
    private val log = LoggerFactory.getLogger(javaClass)

    override fun loadComplete() {
        val app = AndroidApp.get(context)

        //TODO if we minimize and the load finishes in the bg, we need to properly handle that on ui restore
        val currentActivity = app.currentActivity as? MainActivity
        if (currentActivity == null) {
            log.warn("loadComplete called without an activity present")
            return
        }

        currentActivity.hideSplashImage()
    }
}