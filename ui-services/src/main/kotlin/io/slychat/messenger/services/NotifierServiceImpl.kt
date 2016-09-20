package io.slychat.messenger.services

import io.slychat.messenger.core.persistence.ConversationDisplayInfo
import io.slychat.messenger.core.persistence.ConversationId
import io.slychat.messenger.services.config.UserConfig
import io.slychat.messenger.services.config.UserConfigService
import org.slf4j.LoggerFactory
import rx.Observable
import rx.Scheduler
import rx.subscriptions.CompositeSubscription
import java.util.*
import java.util.concurrent.TimeUnit

data class NotificationState(
    val state: List<NotificationConversationInfo>
) {
    companion object {
        val empty = NotificationState(emptyList())
    }
}

data class NotificationConversationInfo(
    val conversationDisplayInfo: ConversationDisplayInfo,
    val hasNew: Boolean
)

/**
 * Listens for new message events and dispatches notification display info to the underlying platform implementation.
 */
class NotifierServiceImpl(
    uiEvents: Observable<UIEvent>,
    conversationInfoUpdates: Observable<ConversationDisplayInfo>,
    uiVisibility: Observable<Boolean>,
    scheduler: Scheduler,
    bufferMs: Long,
    private val platformNotificationService: PlatformNotificationService,
    private val userConfigService: UserConfigService
) : NotifierService {
    companion object {
        internal fun mergeNotificationConversationInfo(
            previousState: Map<ConversationId, NotificationConversationInfo>,
            bufferedInfo: List<ConversationDisplayInfo>
        ): Map<ConversationId, NotificationConversationInfo> {
            val r = HashMap(previousState)

            for (info in bufferedInfo) {
                val conversationId = info.conversationId

                val cached = r[conversationId]

                val unreadCount = info.unreadCount

                //if the unread count never drops to 0, we still have some unread messages
                if (unreadCount <= 0) {
                    r.remove(conversationId)
                    continue
                }

                val prevUnreadCount = cached?.let { it.conversationDisplayInfo.unreadCount } ?: 0

                val hasNew = unreadCount > prevUnreadCount

                r[conversationId] = NotificationConversationInfo(info, hasNew)
            }

            return r
        }
    }

    private var log = LoggerFactory.getLogger(javaClass)

    /** If false, ignore all notifications. Still runs notification clear functions. */
    var enableNotificationDisplay = true
        private set

    private var currentPage: PageType? = null

    private var isUiVisible: Boolean = false

    private val subscriptions = CompositeSubscription()

    private var currentNotificationState: Map<ConversationId, NotificationConversationInfo> = HashMap()

    init {
        require(bufferMs >= 0) { "bufferMs must be >= 0, got $bufferMs" }

        //although this is a hot observable, it hasn't yet started emitting, so we don't need to jump through hoops for this
        val bufferedConversationInfo = if (bufferMs > 0) {
            val multiCast = conversationInfoUpdates.publish().refCount()
            val debounced = multiCast.debounce(bufferMs, TimeUnit.MILLISECONDS, scheduler)
            multiCast.buffer(debounced)
        }
        else {
            conversationInfoUpdates.map { listOf(it) }
        }

        subscriptions.add(uiEvents.subscribe { onUiEvent(it) })
        subscriptions.add(bufferedConversationInfo.subscribe { onConversationInfoUpdate(it) })
        subscriptions.add(uiVisibility.subscribe { onUiVisibilityChange(it) })
    }

    private fun onConversationInfoUpdate(conversationDisplayInfo: List<ConversationDisplayInfo>) {
        if (!enableNotificationDisplay)
            return

        if (isUiVisible && currentPage == PageType.CONTACTS)
            return

        val newNotificationState = mergeNotificationConversationInfo(currentNotificationState, conversationDisplayInfo)
        setNewState(newNotificationState)
    }

    private fun setNewState(newNotificationState: Map<ConversationId, NotificationConversationInfo>) {
        currentNotificationState = newNotificationState

        val notificationState = NotificationState(newNotificationState.values.toList())
        platformNotificationService.updateNotificationState(notificationState)

    }

    private fun onUiVisibilityChange(isVisible: Boolean) {
        log.debug("UI visibility: {}", isVisible)

        isUiVisible = isVisible

        if (isVisible && currentPage == PageType.CONTACTS)
            setNewState(emptyMap())
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
                        setNewState(emptyMap())

                    else -> {}
                }
            }
        }
    }
}
