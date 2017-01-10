package io.slychat.messenger.desktop.osx

import io.slychat.messenger.core.persistence.ConversationId
import io.slychat.messenger.desktop.DesktopApp
import io.slychat.messenger.desktop.osx.ns.NSUserNotification
import io.slychat.messenger.desktop.osx.ns.NSUserNotificationCenter
import io.slychat.messenger.desktop.osx.ns.NSUserNotificationCenterDelegate

class UserNotificationCenterDelegate(private val desktopApp: DesktopApp) : NSUserNotificationCenterDelegate() {
    override fun didActivateNotification(center: NSUserNotificationCenter, notification: NSUserNotification) {
        val type = NotificationType.valueOf(notification.userInfo[OSXNotificationService.USERINFO_TYPE_KEY])

        when (type) {
            NotificationType.CONVERSATION -> {
                val conversationIdString = notification.userInfo[OSXNotificationService.USERINFO_CONVERSATION_ID_KEY] ?: return

                val conversationId = ConversationId.fromString(conversationIdString)
                desktopApp.handleConversationNotificationActivated(conversationId)
            }
        }

        center.removeDeliveredNotification(notification)
    }

    override fun didDeliverNotification(center: NSUserNotificationCenter, notification: NSUserNotification) {
    }

    override fun shouldPresentNotification(center: NSUserNotificationCenter, notification: NSUserNotification): Boolean {
        return false
    }
}
