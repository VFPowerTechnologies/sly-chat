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

/**
 * @property hasNew This indicates whether or not new messages have appeared since the last modification.
 */
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
            val compressed = HashMap<ConversationId, ConversationDisplayInfo>()

            for (info in bufferedInfo)
                compressed[info.conversationId] = info

            val r = HashMap<ConversationId, NotificationConversationInfo>()

            previousState.mapValuesTo(r) {
                it.value.copy(hasNew = false)
            }

            compressed.forEach {
                val (conversationId, conversationDisplayInfo) = it

                val previousIds = previousState[conversationId]?.let { it.conversationDisplayInfo.latestUnreadMessageIds } ?: emptySet<String>()

                val currentIds = HashSet(conversationDisplayInfo.latestUnreadMessageIds)

                val newIds = currentIds - previousIds

                if (currentIds.isEmpty())
                    r.remove(conversationId)
                else
                    r[conversationId] = NotificationConversationInfo(conversationDisplayInfo, newIds.isNotEmpty())
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

    private var currentNotificationState: Map<ConversationId, NotificationConversationInfo> = emptyMap()

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
        updateState(newNotificationState)
    }

    private fun updateState(newNotificationState: Map<ConversationId, NotificationConversationInfo>) {
        currentNotificationState = newNotificationState

        val notificationState = NotificationState(newNotificationState.values.toList())
        platformNotificationService.updateNotificationState(notificationState)
    }

    private fun onUiVisibilityChange(isVisible: Boolean) {
        log.debug("UI visibility: {}", isVisible)

        isUiVisible = isVisible

        if (isVisible && currentPage == PageType.CONTACTS)
            updateState(emptyMap())
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
                        updateState(emptyMap())

                    else -> {}
                }
            }
        }
    }
}
