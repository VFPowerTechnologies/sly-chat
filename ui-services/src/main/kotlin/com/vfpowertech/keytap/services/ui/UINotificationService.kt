package com.vfpowertech.keytap.services.ui

import com.vfpowertech.jsbridge.processor.annotations.JSToJavaGenerate

@JSToJavaGenerate("NotificationService")
interface UINotificationService {
    fun clearMessageNotificationsForUser(contactEmail: String)
    fun clearAllMessageNotifications()
    //XXX maybe fetch the unread count from the db instead?
    fun createNewMessageNotification(contactEmail: String, unreadCount: Int)
}