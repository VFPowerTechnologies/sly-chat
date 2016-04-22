package com.vfpowertech.keytap.android.services

import android.content.Context
import com.vfpowertech.keytap.android.AndroidApp
import com.vfpowertech.keytap.services.ui.UILoadService
import org.slf4j.LoggerFactory

class AndroidUILoadService(val context: Context) : UILoadService {
    private val log = LoggerFactory.getLogger(javaClass)

    override fun loadComplete() {
        val app = AndroidApp.get(context)

        app.uiLoadCompleted()
    }
}