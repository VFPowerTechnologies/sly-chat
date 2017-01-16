package io.slychat.messenger.android

import android.app.Service
import android.content.Intent
import android.os.IBinder
import io.slychat.messenger.core.SlyAddress
import io.slychat.messenger.services.NotificationState

/** Stop receiving GCM notifications. */
class NotificationStopService : Service() {
    companion object {
        val EXTRA_ACCOUNT = "account"
    }

    override fun onBind(intent: Intent): IBinder? {
        throw UnsupportedOperationException()
    }

    override fun onStartCommand(intent: Intent, flags: Int, startId: Int): Int {
        val app = AndroidApp.get(this)

        app.notificationService.updateNotificationState(NotificationState.empty)

        val address = SlyAddress.fromString(intent.getStringExtra(EXTRA_ACCOUNT))!!
        app.stopReceivingNotifications(address)

        stopSelf()

        return START_NOT_STICKY
    }

    override fun onCreate() {}
}