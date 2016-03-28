package com.vfpowertech.keytap.services

import com.vfpowertech.keytap.services.ui.*

//user-scoped
class NotifierService(
    private val messengerService: MessengerService,
    private val uiEventService: UIEventService,
    private val platformNotificationService: PlatformNotificationService
) {
    private var currentPage: PageType? = null
    private var currentlySelectedChatUser: String? = null

    /** Should be called by UI implementations to reflect the visibility state of the UI window. */
    var isUiVisible: Boolean = false

    fun init() {
        uiEventService.events.subscribe { onUiEvent(it) }
        messengerService.newMessages.subscribe { onNewMessages(it) }
    }

    private fun onNewMessages(messageBundle: MessageBundle) {
        if (isUiVisible) {
            if (currentPage == PageType.CONTACTS)
                return

            //don't fire notifications for the currently focused user
            if (messageBundle.contactEmail == currentlySelectedChatUser)
                return
        }

        //TODO add message count
        platformNotificationService.addNewMessageNotification(messageBundle.contactEmail)
    }

    private fun onUiEvent(event: UIEvent) {
        when (event) {
            is PageChangeEvent -> {
                currentPage = event.page
                currentlySelectedChatUser = null

                when (event.page) {
                    PageType.CONVO -> {
                        currentlySelectedChatUser = event.extra
                        platformNotificationService.clearMessageNotificationsForUser(event.extra)
                    }

                    PageType.CONTACTS ->
                        platformNotificationService.clearAllMessageNotifications()
                }
            }
        }
    }
}