package io.slychat.messenger.services.ui.impl

import io.slychat.messenger.core.UserId
import io.slychat.messenger.core.persistence.ConversationDisplayInfo
import io.slychat.messenger.core.persistence.ConversationId
import io.slychat.messenger.core.persistence.GroupId
import io.slychat.messenger.core.persistence.toConversationId
import io.slychat.messenger.services.MessageUpdateEvent
import io.slychat.messenger.services.RelayClock
import io.slychat.messenger.services.di.UserComponent
import io.slychat.messenger.services.messaging.ConversationMessage
import io.slychat.messenger.services.messaging.MessageService
import io.slychat.messenger.services.messaging.MessengerService
import io.slychat.messenger.services.ui.*
import nl.komponents.kovenant.Promise
import nl.komponents.kovenant.functional.map
import org.slf4j.LoggerFactory
import rx.Observable
import rx.subscriptions.CompositeSubscription
import java.util.*

/** This exists for the lifetime of the application. It wraps MessengerService, which exists for the lifetime of the user session. */
class UIMessengerServiceImpl(
    userSessionAvailable: Observable<UserComponent?>
) : UIMessengerService {
    private val log = LoggerFactory.getLogger(javaClass)

    private val newMessageListeners = ArrayList<(UIMessageInfo) -> Unit>()
    private val messageStatusUpdateListeners = ArrayList<(UIMessageUpdateEvent) -> Unit>()
    private val conversationInfoUpdateListeners = ArrayList<(UIConversationDisplayInfo) -> Unit>()
    private val clockDifferenceUpdateListeners = ArrayList<(Long) -> Unit>()

    private var relayClockDiff = 0L

    private val subscriptions = CompositeSubscription()

    private var messengerService: MessengerService? = null
    private var messageService: MessageService? = null
    private var relayClock: RelayClock? = null

    init {
        userSessionAvailable.subscribe { onUserSessionAvailabilityChanged(it) }
    }

    private fun onUserSessionAvailabilityChanged(userComponent: UserComponent?) {
        if (userComponent != null) {
            val messageService = userComponent.messageService

            subscriptions.add(messageService.newMessages.subscribe { onNewMessages(it) })
            subscriptions.add(messageService.messageUpdates.subscribe { onMessageUpdate(it) })
            subscriptions.add(messageService.conversationInfoUpdates.subscribe { onConversationInfoUpdate(it) })
            subscriptions.add(userComponent.relayClock.clockDiffUpdates.subscribe { onClockDifferenceUpdate(it) })

            messengerService = userComponent.messengerService
            relayClock = userComponent.relayClock
            this.messageService = messageService
        }
        else {
            subscriptions.clear()

            messengerService = null
            relayClock = null

            relayClockDiff = 0
        }
    }

    private fun onConversationInfoUpdate(conversationDisplayInfo: ConversationDisplayInfo) {
        val conversationId = conversationDisplayInfo.conversationId
        val (userId, groupId) = when (conversationId) {
            is ConversationId.Group -> null to conversationId.id
            is ConversationId.User -> conversationId.id to null
        }

        val uiLastMessageData = conversationDisplayInfo.lastMessageData?.let {
            UILastMessageData(it.speakerName, it.message, it.timestamp)
        }

        val uiConversationDisplayInfo = UIConversationDisplayInfo(
            userId,
            groupId,
            conversationDisplayInfo.groupName,
            conversationDisplayInfo.unreadCount,
            uiLastMessageData
        )

        conversationInfoUpdateListeners.forEach { it(uiConversationDisplayInfo) }
    }

    private fun onClockDifferenceUpdate(diff: Long) {
        relayClockDiff = diff

        clockDifferenceUpdateListeners.forEach { it(diff) }
    }

    private fun getMessengerServiceOrThrow(): MessengerService {
        return messengerService ?: error("No user session has been established")
    }

    private fun getMessageServiceOrThrow(): MessageService {
        return messageService ?: error("No user session has been established")
    }

    /** First we add to the log, then we display it to the user. */
    private fun onNewMessages(message: ConversationMessage) {
        val messages = listOf(message.info.toUI())

        val uiMessageInfo = when (message) {
            is ConversationMessage.Single -> UIMessageInfo(message.userId, null, messages)
            is ConversationMessage.Group -> UIMessageInfo(message.speaker, message.groupId, messages)
        }

        notifyNewMessageListeners(uiMessageInfo)
    }

    private fun conversationIdToUserGroupPair(conversationId: ConversationId): Pair<UserId?, GroupId?> {
        return when (conversationId) {
            is ConversationId.User -> conversationId.id to null
            is ConversationId.Group -> null to conversationId.id
        }
    }

    private fun onMessageUpdate(event: MessageUpdateEvent) {
        val uiEvent = when (event) {
            is MessageUpdateEvent.Delivered -> {
                val (userId, groupId) = conversationIdToUserGroupPair(event.conversationId)

                UIMessageUpdateEvent.Delivered(userId, groupId, event.messageId, event.deliveredTimestamp)
            }

            is MessageUpdateEvent.Expiring -> {
                val (userId, groupId) = conversationIdToUserGroupPair(event.conversationId)

                UIMessageUpdateEvent.Expiring(userId, groupId, event.messageId, event.ttl, event.expiresAt)
            }

            is MessageUpdateEvent.Expired -> {
                val (userId, groupId) = conversationIdToUserGroupPair(event.conversationId)

                UIMessageUpdateEvent.Expired(userId, groupId, event.messageId)
            }

            else -> null
        }

        if (uiEvent != null)
            notifyMessageStatusUpdateListeners(uiEvent)
    }

    /* Interface methods. */

    override fun sendMessageTo(userId: UserId, message: String, ttl: Long): Promise<Unit, Exception> {
        return getMessengerServiceOrThrow().sendMessageTo(userId, message, ttl)
    }

    override fun sendGroupMessageTo(groupId: GroupId, message: String, ttl: Long): Promise<Unit, Exception> {
        return getMessengerServiceOrThrow().sendGroupMessageTo(groupId, message, ttl)
    }

    override fun addNewMessageListener(listener: (UIMessageInfo) -> Unit) {
        newMessageListeners.add(listener)
    }

    override fun addMessageStatusUpdateListener(listener: (UIMessageUpdateEvent) -> Unit) {
        messageStatusUpdateListeners.add(listener)
    }

    override fun addConversationInfoUpdateListener(listener: (UIConversationDisplayInfo) -> Unit) {
        conversationInfoUpdateListeners.add(listener)
    }

    override fun addClockDifferenceUpdateListener(listener: (Long) -> Unit) {
        clockDifferenceUpdateListeners.add(listener)

        listener(relayClockDiff)
    }

    override fun getLastMessagesFor(userId: UserId, startingAt: Int, count: Int): Promise<List<UIMessage>, Exception> {
        return getMessengerServiceOrThrow().getLastMessagesFor(userId, startingAt, count) map { messages ->
            messages.map { it.info.toUI() }
        }
    }

    override fun getConversations(): Promise<List<UIConversation>, Exception> {
        return getMessengerServiceOrThrow().getConversations() map { convos ->
            convos.map {
                val contact = it.contact
                val info = it.info
                UIConversation(contact.toUI(), UIConversationInfo(true, info.unreadMessageCount, info.lastMessage, info.lastTimestamp))
            }
        }
    }

    private fun notifyNewMessageListeners(messageInfo: UIMessageInfo) {
        newMessageListeners.forEach { it(messageInfo) }
    }

    private fun notifyMessageStatusUpdateListeners(event: UIMessageUpdateEvent) {
        messageStatusUpdateListeners.forEach { it(event) }
    }

    //also fix UIGroupService to use that instead of persistencemanager directly
    override fun deleteAllMessagesFor(userId: UserId): Promise<Unit, Exception> {
        return getMessageServiceOrThrow().deleteAllMessages(userId.toConversationId())
    }

    override fun deleteMessagesFor(userId: UserId, messages: List<String>): Promise<Unit, Exception> {
        return getMessageServiceOrThrow().deleteMessages(userId.toConversationId(), messages)
    }

    override fun startMessageExpiration(userId: UserId, messageId: String): Promise<Unit, Exception> {
        return getMessageServiceOrThrow().startMessageExpiration(userId.toConversationId(), messageId)
    }
}
