package io.slychat.messenger.services

import io.slychat.messenger.core.UserId
import io.slychat.messenger.core.persistence.*
import io.slychat.messenger.services.config.UserConfig
import io.slychat.messenger.services.config.UserConfigService
import io.slychat.messenger.services.contacts.ContactDisplayInfo
import io.slychat.messenger.services.contacts.toContactDisplayInfo
import io.slychat.messenger.services.messaging.MessageBundle
import io.slychat.messenger.services.messaging.MessengerService
import io.slychat.messenger.services.ui.UIEventService
import nl.komponents.kovenant.ui.successUi
import org.slf4j.LoggerFactory

/**
 * Listens for new message events and dispatches notification display info to the underlying platform implementation.
 *
 * Uses UI events to decide when to dispatch notifications, as well as when to clear notifications.
 *
 * Notification display info is only dispatched when certain UI conditions are met.
 *
 * 1) If the current page is recent chats, no notifications are dispatched.
 * 2) If the current focused page is the sender's page, no notifications are dispatched.
 *
 * Notifications are cleared when:
 *
 * 1) If the current navigated to page is recent chats, all notifications are cleared.
 * 2) If the current navigated to page is a user's page, all notifications for that user are cleared.
 */
class NotifierService(
    private val messengerService: MessengerService,
    private val uiEventService: UIEventService,
    private val contactsPersistenceManager: ContactsPersistenceManager,
    private val groupPersistenceManager: GroupPersistenceManager,
    private val platformNotificationService: PlatformNotificationService,
    private val userConfigService: UserConfigService
) {
    private var log = LoggerFactory.getLogger(javaClass)

    /** If false, ignore all notifications. Still runs notification clear functions. */
    var enableNotificationDisplay = true
        private set

    private var currentPage: PageType? = null
    private var currentlySelectedChatUser: UserId? = null

    /** Should be called by UI implementations to reflect the visibility state of the UI window. */
    var isUiVisible: Boolean = false

    fun init() {
        uiEventService.events.subscribe { onUiEvent(it) }
        messengerService.newMessages.subscribe { onNewMessages(it) }

        enableNotificationDisplay = userConfigService.notificationsEnabled

        userConfigService.updates.subscribe { keys ->
            keys.forEach {
                if (it == UserConfig.NOTIFICATIONS_ENABLED)
                    enableNotificationDisplay = userConfigService.notificationsEnabled
            }
        }
    }

    private fun withDisplayInfo(messageBundle: MessageBundle, body: (ContactDisplayInfo) -> Unit) {
        withGroupInfo(messageBundle.groupId) { groupInfo ->
            withContactInfo(messageBundle.userId) { contactInfo ->
                val info = ContactDisplayInfo(
                    contactInfo.id,
                    contactInfo.name,
                    groupInfo?.id,
                    groupInfo?.name
                )

                body(info)
            }
        }
    }

    private fun withGroupInfo(groupId: GroupId?, body: (GroupInfo?) -> Unit) {
        if (groupId != null)
            groupPersistenceManager.getInfo(groupId) successUi {
                if (it != null)
                    body(it)
                else
                    log.warn("Received a MessageBundle for group {}, but unable to find info", groupId)
            } fail { e ->
                log.error("Failure fetching group info: {}", e.message, e)
            }
        else
            body(null)

    }

    private fun withContactInfo(userId: UserId, body: (ContactInfo) -> Unit) {
        contactsPersistenceManager.get(userId) successUi { contactInfo ->
            if (contactInfo != null)
                body(contactInfo)
            else
                log.warn("Received a MessageBundle for user {}, but unable to find info in contacts list", userId)
        } fail { e ->
            log.error("Failure fetching contact info: {}", e.message, e)
        }
    }

    private fun onNewMessages(messageBundle: MessageBundle) {
        if (!enableNotificationDisplay)
            return

        if (isUiVisible) {
            if (currentPage == PageType.CONTACTS)
                return

            //don't fire notifications for the currently focused user
            if (messageBundle.userId == currentlySelectedChatUser)
                return
        }

        val lastMessage = messageBundle.messages.last()

        withDisplayInfo(messageBundle) { contactDisplayInfo ->
            platformNotificationService.addNewMessageNotification(contactDisplayInfo, lastMessage, messageBundle.messages.size)
        }
    }

    private fun onUiEvent(event: UIEvent) {
        when (event) {
            is PageChangeEvent -> {
                currentPage = event.page
                currentlySelectedChatUser = null

                when (event.page) {
                    PageType.CONVO -> {
                        val userId = UserId(event.extra.toLong())
                        currentlySelectedChatUser = userId
                        withContactInfo(userId) { contactInfo ->
                            platformNotificationService.clearMessageNotificationsForUser(contactInfo.toContactDisplayInfo())
                        }
                    }

                    PageType.CONTACTS ->
                        platformNotificationService.clearAllMessageNotifications()
                }
            }
        }
    }
}