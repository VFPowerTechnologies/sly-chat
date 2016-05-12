package io.slychat.messenger.android

import android.app.Service
import android.content.Intent
import android.os.IBinder

/** Just here to receive notification deletion events. */
class NotificationDeletionService : Service() {
    override fun onBind(intent: Intent): IBinder? {
        throw UnsupportedOperationException()
    }

    override fun onCreate() {
        val app = AndroidApp.get(this)

        app.notificationService.clearAllMessageNotifications()

        stopSelf()
    }
}