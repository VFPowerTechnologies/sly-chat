package io.slychat.messenger.services.ui.impl

import io.slychat.messenger.core.persistence.GroupId
import io.slychat.messenger.services.di.UserComponent
import io.slychat.messenger.services.messaging.MessageBundle
import io.slychat.messenger.services.messaging.MessengerService
import io.slychat.messenger.services.ui.*
import nl.komponents.kovenant.Promise
import nl.komponents.kovenant.functional.map
import org.slf4j.LoggerFactory
import rx.Observable
import rx.Subscription
import java.util.*

/** This exists for the lifetime of the application. It wraps MessengerService, which exists for the lifetime of the user session. */
class UIMessengerServiceImpl(
    userSessionAvailable: Observable<UserComponent?>
) : UIMessengerService {
    private val log = LoggerFactory.getLogger(javaClass)

    private val newMessageListeners = ArrayList<(UIMessageInfo) -> Unit>()
    private val messageStatusUpdateListeners = ArrayList<(UIMessageInfo) -> Unit>()

    private var newMessageSub: Subscription? = null
    private var messageStatusUpdateSub: Subscription? = null

    private var messengerService: MessengerService? = null

    init {
        userSessionAvailable.subscribe { onUserSessionAvailabilityChanged(it) }
    }

    private fun onUserSessionAvailabilityChanged(userComponent: UserComponent?) {
        if (userComponent != null) {
            val messengerService = userComponent.messengerService

            newMessageSub = messengerService.newMessages.subscribe { onNewMessages(it) }
            messageStatusUpdateSub = messengerService.messageUpdates.subscribe { onMessageStatusUpdate(it) }

            this.messengerService = userComponent.messengerService
        }
        else {
            newMessageSub?.unsubscribe()
            newMessageSub = null

            messageStatusUpdateSub?.unsubscribe()
            messageStatusUpdateSub = null

            messengerService = null
        }
    }

    private fun getMessengerServiceOrThrow(): MessengerService {
        return messengerService ?: error("No user session has been established")
    }

    /** First we add to the log, then we display it to the user. */
    private fun onNewMessages(messageBundle: MessageBundle) {
        val messages = messageBundle.messages.map { it.toUI() }
        notifyNewMessageListeners(UIMessageInfo(messageBundle.userId, messageBundle.groupId, messages))
    }

    private fun onMessageStatusUpdate(messageBundle: MessageBundle) {
        val messages = messageBundle.messages.map { it.toUI() }
        notifyMessageStatusUpdateListeners(UIMessageInfo(messageBundle.userId, messageBundle.groupId, messages))
    }

    /* Interface methods. */

    override fun sendMessageTo(contact: UIContactDetails, message: String): Promise<UIMessage, Exception> {
        return getMessengerServiceOrThrow().sendMessageTo(contact.id, message) map { messageInfo ->
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

    override fun getLastMessagesFor(contact: UIContactDetails, startingAt: Int, count: Int): Promise<List<UIMessage>, Exception> {
        return getMessengerServiceOrThrow().getLastMessagesFor(contact.id, startingAt, count) map { messages ->
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

    override fun markConversationAsRead(contact: UIContactDetails): Promise<Unit, Exception> {
        return getMessengerServiceOrThrow().markConversationAsRead(contact.id)
    }

    private fun notifyNewMessageListeners(messageInfo: UIMessageInfo) {
        for (listener in newMessageListeners)
            listener(messageInfo)
    }

    private fun notifyMessageStatusUpdateListeners(messageInfo: UIMessageInfo) {
        for (listener in messageStatusUpdateListeners)
            listener(messageInfo)
    }

    override fun deleteAllMessagesFor(contact: UIContactDetails): Promise<Unit, Exception> {
        return getMessengerServiceOrThrow().deleteAllMessages(contact.id)
    }

    override fun deleteMessagesFor(contact: UIContactDetails, messages: List<String>): Promise<Unit, Exception> {
        return getMessengerServiceOrThrow().deleteMessages(contact.id, messages)
    }
}
