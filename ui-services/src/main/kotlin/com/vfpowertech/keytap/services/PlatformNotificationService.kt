package com.vfpowertech.keytap.services

interface PlatformNotificationService {
    fun clearMessageNotificationsForUser(contactEmail: String)
    fun clearAllMessageNotifications()
    fun addNewMessageNotification(contactEmail: String, messageCount: Int)
}