package io.slychat.messenger.services

import io.slychat.messenger.core.persistence.ConversationDisplayInfo

interface PlatformNotificationService {
    fun updateConversationNotification(conversationDisplayInfo: ConversationDisplayInfo)

    fun clearAllMessageNotifications()
}
