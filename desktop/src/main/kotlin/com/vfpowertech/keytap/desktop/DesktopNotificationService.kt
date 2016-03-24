package com.vfpowertech.keytap.desktop

import com.vfpowertech.keytap.services.ui.PlatformNotificationService

//TODO
class DesktopNotificationService : PlatformNotificationService {
    override fun clearMessageNotificationsForUser(contactEmail: String) {
    }

    override fun clearAllMessageNotifications() {
    }

    override fun createNewMessageNotification(contactEmail: String, unreadCount: Int) {
    }
}