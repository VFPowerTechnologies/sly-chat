package com.vfpowertech.keytap.services

interface PlatformNotificationService {
    fun clearMessageNotificationsForUser(contact: ContactDisplayInfo)
    fun clearAllMessageNotifications()
    fun addNewMessageNotification(contact: ContactDisplayInfo, messageCount: Int)
}