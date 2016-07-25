package io.slychat.messenger.services

import io.slychat.messenger.core.persistence.MessageInfo
import io.slychat.messenger.services.contacts.ContactDisplayInfo

interface PlatformNotificationService {
    fun clearMessageNotificationsForUser(contact: ContactDisplayInfo)
    fun clearAllMessageNotifications()
    fun addNewMessageNotification(contact: ContactDisplayInfo, lastMessageInfo: MessageInfo, messageCount: Int)
}