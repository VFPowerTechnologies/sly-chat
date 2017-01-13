package io.slychat.messenger.desktop.osx

import io.slychat.messenger.core.SlyAddress
import io.slychat.messenger.core.persistence.ConversationDisplayInfo
import io.slychat.messenger.desktop.osx.ns.NSApplication
import io.slychat.messenger.desktop.osx.ns.NSMutableDictionary
import io.slychat.messenger.desktop.osx.ns.NSUserNotification
import io.slychat.messenger.desktop.osx.ns.NSUserNotificationCenter
import io.slychat.messenger.services.NotificationState
import io.slychat.messenger.services.PlatformNotificationService
import io.slychat.messenger.services.di.UserComponent
import org.slf4j.LoggerFactory
import rx.Observable

class OSXNotificationService : PlatformNotificationService {
    companion object {
        const val USERINFO_TYPE_KEY = "type"
        const val USERINFO_ACCOUNT_KEY = "account"
        const val USERINFO_CONVERSATION_ID_KEY = "conversationId"
    }

    private val log = LoggerFactory.getLogger(javaClass)

    private var currentAccount: SlyAddress? = null

    fun init(userSessionAvailable: Observable<UserComponent?>) {
        userSessionAvailable.subscribe { onUserSessionAvailabilityChanged(it) }
    }

    private fun onUserSessionAvailabilityChanged(userComponent: UserComponent?) {
        currentAccount = userComponent?.userLoginData?.address
    }

    override fun getNotificationSoundDisplayName(soundUri: String): String {
        TODO()
    }

    override fun updateNotificationState(notificationState: NotificationState) {
        val unreadCount = notificationState.unreadCount()
        NSApplication.sharedApplication.dockTile.badgeLabel = if (unreadCount > 0) unreadCount.toString() else null

        notificationState.state.forEach {
            if (it.hasNew)
                displayNotification(it.conversationDisplayInfo)
        }

        if (unreadCount > 0)
            NSApplication.sharedApplication.requestUserAttention(NSApplication.NSInformationRequest)
    }

    private fun displayNotification(conversationDisplayInfo: ConversationDisplayInfo) {
        if (conversationDisplayInfo.unreadCount == 0)
            return

        //shouldn't occur
        val currentAccount = this.currentAccount
        if (currentAccount == null) {
            log.warn("Received notification info but not logged in")
            return
        }

        val lastMessageData = conversationDisplayInfo.lastMessageData ?: return

        val isExpirable = lastMessageData.message == null
        val extra = if (isExpirable) "secret " else ""

        val notification = NSUserNotification()
        notification.title = "New ${extra}message from ${lastMessageData.speakerName}"

        if (!isExpirable)
            notification.informativeText = lastMessageData.message

        val userInfo = NSMutableDictionary()
        userInfo[USERINFO_ACCOUNT_KEY] = currentAccount.asString()
        userInfo[USERINFO_TYPE_KEY] = NotificationType.CONVERSATION.toString()
        userInfo[USERINFO_CONVERSATION_ID_KEY] = conversationDisplayInfo.conversationId.asString()

        notification.userInfo = userInfo.toNSDictionary()
        notification.soundName = NSUserNotification.DEFAULT_SOUND_NAME

        val userNotificationCenter = NSUserNotificationCenter.defaultUserNotificationCenter

        userNotificationCenter.deliverNotification(notification)
    }
}