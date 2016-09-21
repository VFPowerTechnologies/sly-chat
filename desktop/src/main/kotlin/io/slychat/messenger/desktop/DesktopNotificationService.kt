package io.slychat.messenger.desktop

import io.slychat.messenger.core.persistence.ConversationDisplayInfo
import io.slychat.messenger.services.NotificationState
import io.slychat.messenger.services.PlatformNotificationService
import org.controlsfx.control.Notifications
import org.slf4j.LoggerFactory

class DesktopNotificationService : PlatformNotificationService {
    private val log = LoggerFactory.getLogger(javaClass)


    override fun updateNotificationState(notificationState: NotificationState) {
        notificationState.state.forEach {
            if (it.hasNew)
                displayNotification(it.conversationDisplayInfo)
        }
    }

    private fun displayNotification(conversationDisplayInfo: ConversationDisplayInfo) {
        if (conversationDisplayInfo.unreadCount == 0)
            return

        val lastMessageData = conversationDisplayInfo.lastMessageData

        log.debug(
            "New notification for {}: count={}",
            conversationDisplayInfo.conversationId,
            conversationDisplayInfo.unreadCount
        )

        if (lastMessageData != null) {
            val isExpirable = lastMessageData.message == null

            val extra = if (isExpirable) "secret " else ""

            openNotification("Sly Chat", "You have a new ${extra}message from ${lastMessageData.speakerName}")
        }
    }

    private fun openNotification(title: String, text: String) {
        Notifications.create()
                .darkStyle()
                .title(title)
                .text(text)
                .show()
    }
}