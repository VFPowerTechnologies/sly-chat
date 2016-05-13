package io.slychat.messenger.services

import io.slychat.messenger.core.persistence.MessageInfo

interface PlatformNotificationService {
    fun clearMessageNotificationsForUser(contact: ContactDisplayInfo)
    fun clearAllMessageNotifications()
    fun addNewMessageNotification(contact: ContactDisplayInfo, lastMessageInfo: MessageInfo, messageCount: Int)
}