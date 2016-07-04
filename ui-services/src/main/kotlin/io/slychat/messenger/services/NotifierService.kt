package io.slychat.messenger.services

import io.slychat.messenger.core.UserId
import io.slychat.messenger.core.persistence.ContactInfo
import io.slychat.messenger.core.persistence.ContactsPersistenceManager
import io.slychat.messenger.services.ui.UIEventService
import nl.komponents.kovenant.functional.map
import org.slf4j.LoggerFactory

//user-scoped
class NotifierService(
    private val messengerService: MessengerService,
    private val uiEventService: UIEventService,
    private val contactsPersistenceManager: ContactsPersistenceManager,
    private val platformNotificationService: PlatformNotificationService
) {
    private var log = LoggerFactory.getLogger(javaClass)

    private var currentPage: PageType? = null
    private var currentlySelectedChatUser: UserId? = null

    /** Should be called by UI implementations to reflect the visibility state of the UI window. */
    var isUiVisible: Boolean = false

    fun init() {
        uiEventService.events.subscribe { onUiEvent(it) }
        messengerService.newMessages.subscribe { onNewMessages(it) }
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
