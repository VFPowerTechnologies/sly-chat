package io.slychat.messenger.services

interface PlatformNotificationService {
    fun getNotificationSoundDisplayName(soundUri: String): String

    fun updateNotificationState(notificationState: NotificationState)
}
