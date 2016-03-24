package com.vfpowertech.keytap.services.ui

interface PlatformNotificationService {
    fun clearMessageNotificationsForUser(contactEmail: String)
    fun clearAllMessageNotifications()
    fun createNewMessageNotification(contactEmail: String, unreadCount: Int)
}