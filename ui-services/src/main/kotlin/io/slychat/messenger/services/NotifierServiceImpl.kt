package io.slychat.messenger.services

import io.slychat.messenger.core.persistence.ConversationDisplayInfo
import io.slychat.messenger.services.config.UserConfig
import io.slychat.messenger.services.config.UserConfigService
import org.slf4j.LoggerFactory
import rx.Observable
import rx.subscriptions.CompositeSubscription

/**
 * Listens for new message events and dispatches notification display info to the underlying platform implementation.
 */
class NotifierServiceImpl(
    uiEvents: Observable<UIEvent>,
    conversationInfoUpdates: Observable<ConversationDisplayInfo>,
    uiVisibility: Observable<Boolean>,
    private val platformNotificationService: PlatformNotificationService,
    private val userConfigService: UserConfigService
) : NotifierService {
    private var log = LoggerFactory.getLogger(javaClass)

    /** If false, ignore all notifications. Still runs notification clear functions. */
    var enableNotificationDisplay = true
        private set

    private var currentPage: PageType? = null

    private var isUiVisible: Boolean = false

    private val subscriptions = CompositeSubscription()

    init {
        subscriptions.add(uiEvents.subscribe { onUiEvent(it) })
        subscriptions.add(conversationInfoUpdates.subscribe { onConversationInfoUpdate(it) })
        subscriptions.add(uiVisibility.subscribe { onUiVisibilityChange(it) })
    }

    private fun onConversationInfoUpdate(conversationDisplayInfo: ConversationDisplayInfo) {
        if (!enableNotificationDisplay)
            return

        if (currentPage == PageType.CONTACTS)
            return

        if (conversationDisplayInfo.lastMessageData == null) {
            log.warn("Called with empty message data for {}", conversationDisplayInfo.conversationId)
            return
        }

        platformNotificationService.updateConversationNotification(conversationDisplayInfo)
    }

    private fun onUiVisibilityChange(isVisible: Boolean) {
        log.debug("UI visibility: {}", isVisible)
        isUiVisible = isVisible
    }

    override fun init() {
        enableNotificationDisplay = userConfigService.notificationsEnabled

        userConfigService.updates.subscribe { keys ->
            keys.forEach {
                if (it == UserConfig.NOTIFICATIONS_ENABLED)
                    enableNotificationDisplay = userConfigService.notificationsEnabled
            }
        }
    }

    override fun shutdown() {
        subscriptions.clear()
    }

    private fun onUiEvent(event: UIEvent) {
        when (event) {
            is UIEvent.PageChange -> {
                currentPage = event.page

                log.debug("UI page changed to: {}", event)

                when (event.page) {
                    PageType.CONTACTS ->
                        platformNotificationService.clearAllMessageNotifications()

                    else -> {}
                }
            }
        }
    }
}
