package com.vfpowertech.keytap.services.ui

import com.vfpowertech.jsbridge.processor.annotations.JSToJavaGenerate

@JSToJavaGenerate("NotificationService")
interface UINotificationService {
    fun clearMessageNotificationsForUser(contactEmail: String)
    fun clearAllMessageNotifications()
    /** Adds a new notification for a user or increases the current unread count by 1. */
    fun addNewMessageNotification(contactEmail: String)
}