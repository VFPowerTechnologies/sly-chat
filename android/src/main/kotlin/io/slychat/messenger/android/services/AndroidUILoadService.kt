package io.slychat.messenger.android.services

import android.content.Context
import io.slychat.messenger.android.AndroidApp
import io.slychat.messenger.services.ui.UILoadService

class AndroidUILoadService(val context: Context) : UILoadService {
    override fun loadComplete() {
        val app = AndroidApp.get(context)

//        app.uiLoadCompleted()
    }
}