package io.slychat.messenger.desktop

import io.slychat.messenger.services.PlatformNotificationService
import io.slychat.messenger.services.contacts.NotificationConversationInfo
import io.slychat.messenger.services.contacts.NotificationMessageInfo
import org.slf4j.LoggerFactory
import org.controlsfx.control.Notifications

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

        openNotification("Sly Chat", "You have a new message from ${lastMessageInfo.speakerName}")
    }

    private fun openNotification(title: String, text: String) {
        Notifications.create()
                .darkStyle()
                .title(title)
                .text(text)
                .show()
    }
}