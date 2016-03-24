package com.vfpowertech.keytap.services.ui.impl

import com.vfpowertech.keytap.services.ui.PlatformNotificationService
import com.vfpowertech.keytap.services.ui.UINotificationService

class UINotificationServiceImpl(private val platformNotificationService: PlatformNotificationService) : UINotificationService {
    override fun clearMessageNotificationsForUser(contactEmail: String) {
        platformNotificationService.clearMessageNotificationsForUser(contactEmail)
    }

    override fun clearAllMessageNotifications() {
        platformNotificationService.clearAllMessageNotifications()
    }

    override fun addNewMessageNotification(contactEmail: String) {
        platformNotificationService.addNewMessageNotification(contactEmail)
    }
}