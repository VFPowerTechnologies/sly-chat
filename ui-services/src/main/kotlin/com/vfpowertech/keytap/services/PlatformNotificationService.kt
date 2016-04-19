package com.vfpowertech.keytap.services

import com.vfpowertech.keytap.core.persistence.MessageInfo

interface PlatformNotificationService {
    fun clearMessageNotificationsForUser(contact: ContactDisplayInfo)
    fun clearAllMessageNotifications()
    fun addNewMessageNotification(contact: ContactDisplayInfo, lastMessageInfo: MessageInfo, messageCount: Int)
}