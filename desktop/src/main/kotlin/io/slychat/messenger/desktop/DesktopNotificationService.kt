package io.slychat.messenger.desktop

import io.slychat.messenger.services.PlatformNotificationService
import io.slychat.messenger.services.contacts.NotificationConversationInfo
import io.slychat.messenger.services.contacts.NotificationMessageInfo
import org.slf4j.LoggerFactory

class DesktopNotificationService : PlatformNotificationService {
    private val log = LoggerFactory.getLogger(javaClass)

    override fun clearAllMessageNotifications() {
        log.info("Clearing all notifications")
    }

    override fun clearMessageNotificationsFor(notificationConversationInfo: NotificationConversationInfo) {
        log.info("Clearing notifications for key={}", notificationConversationInfo.key)
    }

    override fun addNewMessageNotification(notificationConversationInfo: NotificationConversationInfo, lastMessageInfo: NotificationMessageInfo, messageCount: Int) {
        log.info(
            "New notification for key={}: {} said: {}; count={}",
            notificationConversationInfo.key,
            lastMessageInfo.speakerName,
            lastMessageInfo.message,
            messageCount
        )
    }
}