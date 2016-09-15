package io.slychat.messenger.desktop

import io.slychat.messenger.core.persistence.ConversationDisplayInfo
import io.slychat.messenger.services.PlatformNotificationService
import org.controlsfx.control.Notifications
import org.slf4j.LoggerFactory

class DesktopNotificationService : PlatformNotificationService {
    private val log = LoggerFactory.getLogger(javaClass)

    override fun clearAllMessageNotifications() {
        log.info("Clearing all notifications")
    }

    override fun updateConversationNotification(conversationDisplayInfo: ConversationDisplayInfo) {
        val lastMessageData = conversationDisplayInfo.lastMessageData

        val messageInfo = if (lastMessageData != null)
            "; ${lastMessageData.speakerName} said: ${lastMessageData.message}"
        else
            ""

        log.info(
            "New notification for {}: count={}{}",
            conversationDisplayInfo.conversationId,
            conversationDisplayInfo.unreadCount,
            messageInfo
        )

        if (lastMessageData != null)
            openNotification("Sly Chat", "You have a new message from ${lastMessageData.speakerName}")
    }

    private fun openNotification(title: String, text: String) {
        Notifications.create()
                .darkStyle()
                .title(title)
                .text(text)
                .show()
    }
}