package io.slychat.messenger.android

import android.app.Service
import android.content.Intent
import android.os.IBinder

/** Stop receiving GCM notifications. */
class NotificationStopService : Service() {
    override fun onBind(intent: Intent): IBinder? {
        throw UnsupportedOperationException()
    }

    override fun onCreate() {
        val app = AndroidApp.get(this)
        app.notificationService.clearAllMessageNotifications()
        app.deleteGCMToken()
        stopSelf()
    }
}