package io.slychat.messenger.services

import io.slychat.messenger.services.contacts.NotificationConversationInfo
import io.slychat.messenger.services.contacts.NotificationMessageInfo

interface PlatformNotificationService {
    fun addNewMessageNotification(notificationConversationInfo: NotificationConversationInfo, lastMessageInfo: NotificationMessageInfo, messageCount: Int)
    fun clearMessageNotificationsFor(notificationConversationInfo: NotificationConversationInfo)
    fun clearAllMessageNotifications()
}