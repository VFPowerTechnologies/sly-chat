package io.slychat.messenger.ios

import io.slychat.messenger.services.NotificationState
import io.slychat.messenger.services.PlatformNotificationService

class IOSNotificationService : PlatformNotificationService {
    override fun getNotificationSoundDisplayName(soundUri: String): String {
        return "not-implemented"
    }

    override fun updateNotificationState(notificationState: NotificationState) {
    }
}