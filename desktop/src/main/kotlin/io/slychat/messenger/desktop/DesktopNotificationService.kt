package io.slychat.messenger.desktop

import io.slychat.messenger.core.UserId
import io.slychat.messenger.core.persistence.MessageInfo
import io.slychat.messenger.services.ContactDisplayInfo
import io.slychat.messenger.services.PlatformNotificationService

//TODO
class DesktopNotificationService : PlatformNotificationService {
    override fun clearAllMessageNotifications() {
    }

    override fun clearMessageNotificationsForUser(contact: ContactDisplayInfo) {
    }

    override fun addNewMessageNotification(contact: ContactDisplayInfo, lastMessageInfo: MessageInfo, messageCount: Int) {
    }
}