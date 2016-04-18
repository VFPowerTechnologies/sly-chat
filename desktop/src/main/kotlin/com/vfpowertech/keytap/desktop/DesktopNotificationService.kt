package com.vfpowertech.keytap.desktop

import com.vfpowertech.keytap.core.UserId
import com.vfpowertech.keytap.services.ContactDisplayInfo
import com.vfpowertech.keytap.services.PlatformNotificationService

//TODO
class DesktopNotificationService : PlatformNotificationService {
    override fun clearAllMessageNotifications() {
    }

    override fun clearMessageNotificationsForUser(contact: ContactDisplayInfo) {
    }

    override fun addNewMessageNotification(contact: ContactDisplayInfo, messageCount: Int) {
    }
}