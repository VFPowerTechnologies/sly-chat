package io.slychat.messenger.services

import io.slychat.messenger.core.UserId
import io.slychat.messenger.core.persistence.ContactInfo
import io.slychat.messenger.core.persistence.ContactsPersistenceManager
import io.slychat.messenger.services.config.UserConfig
import io.slychat.messenger.services.config.UserConfigService
import io.slychat.messenger.services.contacts.toContactDisplayInfo
import io.slychat.messenger.services.messaging.MessageBundle
import io.slychat.messenger.services.messaging.MessengerService
import io.slychat.messenger.services.ui.UIEventService
import org.slf4j.LoggerFactory

//user-scoped
class NotifierService(
    private val messengerService: MessengerService,
    private val uiEventService: UIEventService,
    private val contactsPersistenceManager: ContactsPersistenceManager,
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

    private fun withContactInfo(userId: UserId, body: (ContactInfo) -> Unit) {
        contactsPersistenceManager.get(userId) mapUi { contactInfo ->
            if (contactInfo != null)
                body(contactInfo)
            else
                log.warn("Received a MessageBundle for user $userId, but unable to find info in contacts list")
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

        withContactInfo(messageBundle.userId) { contactInfo ->
            platformNotificationService.addNewMessageNotification(contactInfo.toContactDisplayInfo(), lastMessage, messageBundle.messages.size)
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