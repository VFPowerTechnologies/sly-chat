package io.slychat.messenger.desktop.osx

import ca.weblite.objc.NSObject
import ca.weblite.objc.Proxy
import ca.weblite.objc.annotations.Msg
import io.slychat.messenger.core.persistence.ConversationId
import io.slychat.messenger.desktop.osx.OSXNotificationService.Companion.USERINFO_CONVERSATION_ID_KEY
import io.slychat.messenger.desktop.osx.OSXNotificationService.Companion.USERINFO_TYPE_KEY
import io.slychat.messenger.desktop.osx.ns.NSNotification
import io.slychat.messenger.desktop.osx.ns.NSUserNotification

class StartupNotificationObserver : NSObject() {
    init {
        init("NSObject")
    }

    var startupConversationId: ConversationId? = null
        private set

    fun clear() {
        startupConversationId = null
    }

    private fun handleStartupNotification(userNotification: NSUserNotification) {
        val userInfo = userNotification.userInfo

        val typeString = userInfo[USERINFO_TYPE_KEY] ?: return
        val type = try {
            NotificationType.valueOf(typeString)
        }
        catch (e: IllegalArgumentException) {
            return
        }

        if (type == NotificationType.CONVERSATION) {
            val conversationIdString = userInfo[USERINFO_CONVERSATION_ID_KEY] ?: return
            startupConversationId = ConversationId.fromString(conversationIdString)
        }
    }

    //this is called on the main javafx application thread (since the NSApplication main loop is run on there)
    @Suppress("unused")
    @Msg(selector = "handleNotification:", signature = "v@:@")
    fun callback(notificationProxy: Proxy) {
        val notification = NSNotification(notificationProxy)

        val userInfo = notification.userInfo ?: return

        val maybeNotification = userInfo["NSApplicationLaunchUserNotificationKey"] ?: return

        val proxy = maybeNotification as Proxy

        //we process the notification here, to avoid needing to muck around with retain/release calls on the object
        handleStartupNotification(NSUserNotification(proxy))
    }
}