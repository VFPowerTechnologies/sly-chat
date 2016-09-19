package io.slychat.messenger.services

import com.nhaarman.mockito_kotlin.*
import io.slychat.messenger.core.*
import io.slychat.messenger.core.persistence.ConversationMessageInfo
import io.slychat.messenger.services.messaging.ConversationMessage
import io.slychat.messenger.services.messaging.MessageService
import io.slychat.messenger.services.messaging.MessengerService
import io.slychat.messenger.testutils.thenResolveUnit
import org.junit.Before
import org.junit.Test
import rx.subjects.PublishSubject

class MessageReadWatcherImplTest {
    private val messageService: MessageService = mock()
    private val messengerService: MessengerService = mock()

    private val newMessages: PublishSubject<ConversationMessage> = PublishSubject.create()
    private val messageUpdateEvents: PublishSubject<MessageUpdateEvent> = PublishSubject.create()

    @Before
    fun before() {
        whenever(messageService.newMessages).thenReturn(newMessages)
        whenever(messageService.messageUpdates).thenReturn(messageUpdateEvents)

        whenever(messengerService.broadcastMessagesRead(any(), any())).thenResolveUnit()
    }

    private fun createWatcher(): MessageReadWatcherImpl {
        return MessageReadWatcherImpl(messageService, messengerService)
    }

    private fun testBroadcastOnRead(fromSync: Boolean) {
        val watcher = createWatcher()

        val conversationId = randomUserConversationId()
        val messageIds = randomMessageIds()

        val event = MessageUpdateEvent.Read(conversationId, messageIds, fromSync)

        messageUpdateEvents.onNext(event)

        if (!fromSync)
            verify(messengerService).broadcastMessagesRead(conversationId, messageIds)
        else
            verify(messengerService, never()).broadcastMessagesRead(any(), any())
    }

    @Test
    fun `it should call broadcastMessagesRead when a Read(fromSync=false) event is received`() {
        testBroadcastOnRead(false)
    }

    @Test
    fun `it should not call broadcastMessagesRead when a Read(fromSync=true) event is received`() {
        testBroadcastOnRead(false)
    }

    @Test
    fun `it should not call broadcastMessagesRead when receiving sent messages`() {
        val watcher = createWatcher()

        val conversationMessage = ConversationMessage(
            randomUserConversationId(),
            randomSentConversationMessageInfo()
        )

        newMessages.onNext(conversationMessage)

        verify(messengerService, never()).broadcastMessagesRead(any(), any())
    }

    private fun testBroadcastOnNew(isRead: Boolean) {
        val watcher = createWatcher()

        val conversationId = randomUserConversationId()

        val conversationMessage = ConversationMessage(
            conversationId,
            ConversationMessageInfo(
                randomUserId(),
                randomReceivedMessageInfo(isRead)
            )
        )
        val messageId = conversationMessage.conversationMessageInfo.info.id

        newMessages.onNext(conversationMessage)

        if (!isRead)
            verify(messengerService, never()).broadcastMessagesRead(any(), any())
        else
            verify(messengerService).broadcastMessagesRead(conversationId, listOf(messageId))
    }

    @Test
    fun `it should not call broadcastMessagesRead when receiving messages with isRead=false`() {
        testBroadcastOnNew(false)
    }

    @Test
    fun `it should call broadcastMessagesRead when receiving messages with isRead=true`() {
        testBroadcastOnNew(true)
    }
}