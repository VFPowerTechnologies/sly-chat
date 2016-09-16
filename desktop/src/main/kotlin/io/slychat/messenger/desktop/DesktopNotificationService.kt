package io.slychat.messenger.desktop

import io.slychat.messenger.core.persistence.ConversationDisplayInfo
import io.slychat.messenger.services.PlatformNotificationService
import org.controlsfx.control.Notifications
import org.slf4j.LoggerFactory

class DesktopNotificationService : PlatformNotificationService {
    private val log = LoggerFactory.getLogger(javaClass)

    override fun clearAllMessageNotifications() {
        log.debug("Clearing all notifications")
    }

    override fun updateConversationNotification(conversationDisplayInfo: ConversationDisplayInfo) {
        if (conversationDisplayInfo.unreadCount == 0)
            return

        val lastMessageData = conversationDisplayInfo.lastMessageData

        log.debug(
            "New notification for {}: count={}",
            conversationDisplayInfo.conversationId,
            conversationDisplayInfo.unreadCount
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