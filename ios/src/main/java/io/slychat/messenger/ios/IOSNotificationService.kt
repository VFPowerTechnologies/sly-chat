package io.slychat.messenger.ios

import apple.NSObject
import apple.foundation.NSDictionary
import apple.uikit.UIApplication
import apple.uikit.UILocalNotification
import apple.uikit.enums.UIApplicationState
import io.slychat.messenger.core.persistence.ConversationDisplayInfo
import io.slychat.messenger.core.pushnotifications.OfflineMessagesPushNotification
import io.slychat.messenger.services.NotificationState
import io.slychat.messenger.services.PlatformNotificationService
import org.slf4j.LoggerFactory

class IOSNotificationService : PlatformNotificationService {
    companion object {
        val CONVERSATION_ID_KEY = "conversationId"
    }

    private val log = LoggerFactory.getLogger(javaClass)

    override fun getNotificationSoundDisplayName(soundUri: String): String {
        return "not-implemented"
    }

    override fun updateNotificationState(notificationState: NotificationState) {
        val unreadCount = notificationState.state.fold(0L) { current, notificationConversationInfo ->
            current + notificationConversationInfo.conversationDisplayInfo.unreadCount
        }

        UIApplication.sharedApplication().setApplicationIconBadgeNumber(unreadCount)

        log.debug("Setting badge count to {}", unreadCount)

        //we have in-app notifications for new messages in this case
        if (UIApplication.sharedApplication().applicationState() == UIApplicationState.Active)
            return

        notificationState.state.forEach {
            if (it.hasNew)
                fireNotification(it.conversationDisplayInfo)
        }
    }

    private fun fireNotification(conversationDisplayInfo: ConversationDisplayInfo) {
        val conversationIdString = conversationDisplayInfo.conversationId.asString()

        val lastMessageData = conversationDisplayInfo.lastMessageData!!

        val speakerName = lastMessageData.speakerName ?: "Me"
        val message = lastMessageData.message ?: "<Secret message>"

        val notification = UILocalNotification.alloc().init()

        val userInfo = NSDictionary.dictionaryWithObjectsAndKeys<String, NSObject>(
            conversationIdString, CONVERSATION_ID_KEY, null
        )

        notification.setUserInfo(userInfo)

        //title isn't shown when in the background
        notification.setAlertTitle("Message in ${conversationDisplayInfo.conversationName}")

        notification.setAlertBody("$speakerName: $message")

        UIApplication.sharedApplication().presentLocalNotificationNow(notification)
    }

    fun showLoggedOutNotification(message: OfflineMessagesPushNotification) {
        val notification = UILocalNotification.alloc().init()

        notification.setAlertTitle(message.getNotificationTitle())

        notification.setAlertBody(message.getNotificationText())

        notification.setCategory(IOSApp.ACTION_CATEGORY_OFFLINE)

        val userInfo = NSDictionary.dictionaryWithObjectsAndKeys<String, Any>(
            message.account.asString(), IOSApp.USERINFO_ADDRESS,
            null
        )

        notification.setUserInfo(userInfo)

        UIApplication.sharedApplication().presentLocalNotificationNow(notification)
    }
}