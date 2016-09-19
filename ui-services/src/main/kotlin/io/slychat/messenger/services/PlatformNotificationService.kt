package io.slychat.messenger.services

interface PlatformNotificationService {
    fun updateNotificationState(notificationState: NotificationState)

    fun clearAllMessageNotifications()
}
