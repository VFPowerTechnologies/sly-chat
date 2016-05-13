package io.slychat.messenger.android.services

import android.content.Context
import io.slychat.messenger.android.AndroidApp
import io.slychat.messenger.services.ui.UILoadService
import org.slf4j.LoggerFactory

class AndroidUILoadService(val context: Context) : UILoadService {
    private val log = LoggerFactory.getLogger(javaClass)

    override fun loadComplete() {
        val app = AndroidApp.get(context)

        app.uiLoadCompleted()
    }
}