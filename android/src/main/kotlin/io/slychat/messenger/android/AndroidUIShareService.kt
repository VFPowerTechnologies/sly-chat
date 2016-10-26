package io.slychat.messenger.android

import android.content.Context
import io.slychat.messenger.services.ui.UIShareService

class AndroidUIShareService(
    private val context: Context
) : UIShareService {
    override fun isSupported(): Boolean {
        return true
    }

    override fun inviteToSly(subject: String, text: String, htmlText: String) {
        val androidApp = AndroidApp.get(context)
        androidApp.currentActivity?.inviteToSly(subject, text, htmlText)
    }
}