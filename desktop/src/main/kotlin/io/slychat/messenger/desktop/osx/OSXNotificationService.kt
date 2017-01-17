package io.slychat.messenger.desktop.osx

import io.slychat.messenger.core.SlyAddress
import io.slychat.messenger.core.mapToSet
import io.slychat.messenger.core.persistence.ConversationDisplayInfo
import io.slychat.messenger.core.persistence.ConversationId
import io.slychat.messenger.desktop.osx.ns.NSApplication
import io.slychat.messenger.desktop.osx.ns.NSMutableDictionary
import io.slychat.messenger.desktop.osx.ns.NSUserNotification
import io.slychat.messenger.desktop.osx.ns.NSUserNotificationCenter
import io.slychat.messenger.services.NotificationState
import io.slychat.messenger.services.PlatformNotificationService
import io.slychat.messenger.services.di.UserComponent
import nl.komponents.kovenant.task
import org.slf4j.LoggerFactory
import rx.Observable
import rx.subjects.BehaviorSubject

class OSXNotificationService(uiVisibility: BehaviorSubject<Boolean>) : PlatformNotificationService {
    companion object {
        const val USERINFO_TYPE_KEY = "type"
        const val USERINFO_ACCOUNT_KEY = "account"
        const val USERINFO_CONVERSATION_ID_KEY = "conversationId"
    }

    private var isUIVisible = false

    init {
        uiVisibility.subscribe {
            isUIVisible = it
        }
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

    /** Removes all notifications where the user has no unread messages. */
    private fun removeReadConversationNotifications(presentUsers: Set<ConversationId>) {
        val userNotificationCenter = NSUserNotificationCenter.defaultUserNotificationCenter

        for (notification in userNotificationCenter.deliveredNotifications()) {
            val userInfo = notification.userInfo

            val typeString = userInfo[USERINFO_TYPE_KEY] ?: continue
            val type = try {
                NotificationType.valueOf(typeString)
            }
            catch (e: IllegalArgumentException) {
                continue
            }

            if (type == NotificationType.CONVERSATION) {
                val conversationIdString = userInfo[USERINFO_CONVERSATION_ID_KEY] ?: continue
                val conversationId = ConversationId.fromString(conversationIdString)

                if (conversationId !in presentUsers) {
                    log.debug("Removing notification from {}", conversationId)
                    userNotificationCenter.removeDeliveredNotification(notification)
                }
            }
        }
    }

    override fun updateNotificationState(notificationState: NotificationState) {
        val unreadCount = notificationState.unreadCount()
        NSApplication.sharedApplication.dockTile.badgeLabel = if (unreadCount > 0) unreadCount.toString() else null

        if (!isUIVisible) {
            notificationState.state.forEach {
                if (it.hasNew)
                    displayNotification(it.conversationDisplayInfo)
            }
        }

        if (unreadCount > 0)
            NSApplication.sharedApplication.requestUserAttention(NSApplication.NSInformationRequest)

        val presentUsers = notificationState.state.mapToSet { it.conversationDisplayInfo.conversationId }

        //since this involves IPC, and there's no method to remove multiple notifications at once, we just do this off the main thread
        task {
            removeReadConversationNotifications(presentUsers)
        } fail {
            log.error("Failed to remove read notifications: {}", it.message, it)
        }
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