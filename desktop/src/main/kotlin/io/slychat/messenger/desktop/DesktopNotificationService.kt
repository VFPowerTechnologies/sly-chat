package io.slychat.messenger.desktop

import io.slychat.messenger.core.persistence.MessageInfo
import io.slychat.messenger.services.contacts.ContactDisplayInfo
import io.slychat.messenger.services.PlatformNotificationService
import org.slf4j.LoggerFactory

//TODO
class DesktopNotificationService : PlatformNotificationService {
    private val log = LoggerFactory.getLogger(javaClass)

    override fun clearAllMessageNotifications() {
        log.info("Clearing notifications")
    }

    override fun clearMessageNotificationsForUser(contact: ContactDisplayInfo) {
        log.info("Clearing notifications for {} ({})", contact.name, contact.id.long)
    }

    override fun addNewMessageNotification(contact: ContactDisplayInfo, lastMessageInfo: MessageInfo, messageCount: Int) {
        log.info("New notification from {} ({}); count={}", contact.name, contact.id.long, messageCount)
    }
}