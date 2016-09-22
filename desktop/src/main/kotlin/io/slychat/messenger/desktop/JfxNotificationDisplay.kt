package io.slychat.messenger.desktop

import org.controlsfx.control.Notifications

class JfxNotificationDisplay : NotificationDisplay {
    override fun displayNotification(title: String, text: String) {
        Notifications.create()
            .darkStyle()
            .title(title)
            .text(text)
            .show()
    }
}