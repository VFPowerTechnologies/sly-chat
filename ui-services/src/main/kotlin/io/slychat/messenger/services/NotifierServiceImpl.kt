package io.slychat.messenger.services

import io.slychat.messenger.core.UserId
import io.slychat.messenger.core.persistence.*
import io.slychat.messenger.services.config.UserConfig
import io.slychat.messenger.services.config.UserConfigService
import io.slychat.messenger.services.contacts.NotificationConversationInfo
import io.slychat.messenger.services.contacts.NotificationKey
import io.slychat.messenger.services.contacts.NotificationMessageInfo
import io.slychat.messenger.services.messaging.ConversationMessage
import io.slychat.messenger.services.messaging.MessageBundle
import nl.komponents.kovenant.ui.successUi
import org.slf4j.LoggerFactory
import rx.Observable
import java.util.*

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
//XXX this needs a serious redesign
class NotifierServiceImpl(
    newMessages: Observable<MessageBundle>,
    uiEvents: Observable<UIEvent>,
    uiVisibility: Observable<Boolean>,
    private val contactsPersistenceManager: ContactsPersistenceManager,
    private val groupPersistenceManager: GroupPersistenceManager,
    private val platformNotificationService: PlatformNotificationService,
    private val userConfigService: UserConfigService
) : NotifierService {
    companion object {
        /** Groups and flatten a list of message bundles. */
        fun flattenMessageBundles(observable: Observable<List<ConversationMessage>>): Observable<MessageBundle> {
            return observable
                .map { messages ->
                    val grouped = HashMap<Pair<UserId, GroupId?>, MessageBundle>()

                    messages.forEach {
                        val key = when (it) {
                            is ConversationMessage.Single -> it.userId to null
                            is ConversationMessage.Group -> it.speaker!! to it.groupId
                        }

                        val existing = grouped[key]

                        grouped[key] = if (existing == null) {
                            when (it) {
                                is ConversationMessage.Single -> MessageBundle(it.userId, null, listOf(it.info))
                                is ConversationMessage.Group -> MessageBundle(it.speaker!!, it.groupId, listOf(it.info))
                            }
                        }
                        else
                            existing.copy(messages = existing.messages + it.info)

                    }

                    grouped.values
                }
                .flatMapIterable { it }
        }
    }

    private var log = LoggerFactory.getLogger(javaClass)

    /** If false, ignore all notifications. Still runs notification clear functions. */
    var enableNotificationDisplay = true
        private set

    private var currentPage: PageType? = null
    private var currentlySelectedChatUser: UserId? = null
    private var currentlySelectedGroup: GroupId? = null

    private var isUiVisible: Boolean = false

    init {
        uiEvents.subscribe { onUiEvent(it) }
        newMessages.subscribe { onNewMessages(it) }
        uiVisibility.subscribe { onUiVisibilityChange(it) }
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

    private fun withMessageNotificationInfo(messageBundle: MessageBundle, body: (NotificationConversationInfo, NotificationMessageInfo) -> Unit) {
        withMaybeGroupInfo(messageBundle.groupId) { groupInfo ->
            withContactInfo(messageBundle.userId) { contactInfo ->
                val key = if (groupInfo == null)
                    NotificationKey.idToKey(messageBundle.userId)
                else
                    NotificationKey.idToKey(groupInfo.id)

                val info = NotificationConversationInfo(
                    key,
                    groupInfo?.name
                )

                val notificationMessageInfo = getMessageInfo(contactInfo, messageBundle)

                body(info, notificationMessageInfo)
            }
        }
    }

    private fun getMessageInfo(speakerInfo: ContactInfo, messageBundle: MessageBundle): NotificationMessageInfo {
        val last = messageBundle.messages.last()

        return NotificationMessageInfo(
            speakerInfo.name,
            last.message,
            last.timestamp
        )
    }

    private fun withGroupInfo(groupId: GroupId, body: (GroupInfo) -> Unit) {
        groupPersistenceManager.getInfo(groupId) successUi {
            if (it != null)
                body(it)
            else
                log.warn("Received a MessageBundle for group {}, but unable to find info", groupId)
        } fail { e ->
            log.error("Failure fetching group info: {}", e.message, e)
        }
    }

    private fun withMaybeGroupInfo(groupId: GroupId?, body: (GroupInfo?) -> Unit) {
        if (groupId != null)
            withGroupInfo(groupId, body)
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
            if (messageBundle.groupId == null && messageBundle.userId == currentlySelectedChatUser)
                return

            if (currentlySelectedGroup != null && messageBundle.groupId == currentlySelectedGroup)
                return
        }

        withMessageNotificationInfo(messageBundle) { conversationInfo, messageInfo ->
            platformNotificationService.addNewMessageNotification(conversationInfo, messageInfo, messageBundle.messages.size)
        }
    }

    private fun onUiEvent(event: UIEvent) {
        when (event) {
            is PageChangeEvent -> {
                currentPage = event.page
                currentlySelectedChatUser = null
                currentlySelectedGroup = null

                log.debug("UI page changed to: {}", event)

                when (event.page) {
                    PageType.CONVO -> {
                        val userId = UserId(event.extra.toLong())
                        currentlySelectedChatUser = userId
                        withContactInfo(userId) { contactInfo ->
                            val info = NotificationConversationInfo.from(contactInfo)
                            platformNotificationService.clearMessageNotificationsFor(info)
                        }
                    }

                    PageType.CONTACTS ->
                        platformNotificationService.clearAllMessageNotifications()

                    PageType.GROUP -> {
                        val groupId = GroupId(event.extra)
                        currentlySelectedGroup = groupId
                        withGroupInfo(groupId) { groupInfo ->
                            val info = NotificationConversationInfo.from(groupInfo)
                            platformNotificationService.clearMessageNotificationsFor(info)
                        }
                    }
                }
            }
        }
    }
}