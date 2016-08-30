package io.slychat.messenger.services.ui.impl

import io.slychat.messenger.core.UserId
import io.slychat.messenger.core.persistence.GroupId
import io.slychat.messenger.services.RelayClock
import io.slychat.messenger.services.di.UserComponent
import io.slychat.messenger.services.messaging.ConversationMessage
import io.slychat.messenger.services.messaging.MessageBundle
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
    private val messageStatusUpdateListeners = ArrayList<(UIMessageInfo) -> Unit>()
    private val clockDifferenceUpdateListeners = ArrayList<(Long) -> Unit>()

    private val subscriptions = CompositeSubscription()

    private var messengerService: MessengerService? = null
    private var relayClock: RelayClock? = null

    init {
        userSessionAvailable.subscribe { onUserSessionAvailabilityChanged(it) }
    }

    private fun onUserSessionAvailabilityChanged(userComponent: UserComponent?) {
        if (userComponent != null) {
            val messengerService = userComponent.messengerService

            subscriptions.add(messengerService.newMessages.subscribe { onNewMessages(it) })
            subscriptions.add(messengerService.messageUpdates.subscribe { onMessageStatusUpdate(it) })
            subscriptions.add(userComponent.relayClock.clockDiffUpdates.subscribe { onClockDifferenceUpdate(it) })

            this.messengerService = userComponent.messengerService
            relayClock = userComponent.relayClock
        }
        else {
            subscriptions.clear()

            messengerService = null
            relayClock = null
        }
    }

    private fun onClockDifferenceUpdate(diff: Long) {
        clockDifferenceUpdateListeners.forEach { it(diff) }
    }

    private fun getMessengerServiceOrThrow(): MessengerService {
        return messengerService ?: error("No user session has been established")
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

    private fun onMessageStatusUpdate(messageBundle: MessageBundle) {
        val messages = messageBundle.messages.map { it.toUI() }
        notifyMessageStatusUpdateListeners(UIMessageInfo(messageBundle.userId, messageBundle.groupId, messages))
    }

    /* Interface methods. */

    override fun sendMessageTo(userId: UserId, message: String): Promise<UIMessage, Exception> {
        return getMessengerServiceOrThrow().sendMessageTo(userId, message) map { messageInfo ->
            messageInfo.toUI()
        }
    }

    override fun sendGroupMessageTo(groupId: GroupId, message: String): Promise<UIMessage, Exception> {
        return getMessengerServiceOrThrow().sendGroupMessageTo(groupId, message) map {
            it.info.toUI()
        }
    }

    override fun addNewMessageListener(listener: (UIMessageInfo) -> Unit) {
        newMessageListeners.add(listener)
    }

    override fun addMessageStatusUpdateListener(listener: (UIMessageInfo) -> Unit) {
        messageStatusUpdateListeners.add(listener)
    }

    override fun addConversationStatusUpdateListener(listener: (UIConversation) -> Unit) {
        log.debug("addConversationStatusUpdateListener: TODO")
    }

    override fun addClockDifferenceUpdateListener(listener: (Long) -> Unit) {
        clockDifferenceUpdateListeners.add(listener)
    }

    override fun getLastMessagesFor(userId: UserId, startingAt: Int, count: Int): Promise<List<UIMessage>, Exception> {
        return getMessengerServiceOrThrow().getLastMessagesFor(userId, startingAt, count) map { messages ->
            messages.map { it.toUI() }
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

    override fun markConversationAsRead(userId: UserId): Promise<Unit, Exception> {
        return getMessengerServiceOrThrow().markConversationAsRead(userId)
    }

    private fun notifyNewMessageListeners(messageInfo: UIMessageInfo) {
        for (listener in newMessageListeners)
            listener(messageInfo)
    }

    private fun notifyMessageStatusUpdateListeners(messageInfo: UIMessageInfo) {
        for (listener in messageStatusUpdateListeners)
            listener(messageInfo)
    }

    override fun deleteAllMessagesFor(userId: UserId): Promise<Unit, Exception> {
        return getMessengerServiceOrThrow().deleteAllMessages(userId)
    }

    override fun deleteMessagesFor(userId: UserId, messages: List<String>): Promise<Unit, Exception> {
        return getMessengerServiceOrThrow().deleteMessages(userId, messages)
    }
}
