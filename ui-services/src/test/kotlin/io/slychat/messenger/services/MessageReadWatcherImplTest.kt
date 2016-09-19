package io.slychat.messenger.services

import com.nhaarman.mockito_kotlin.*
import io.slychat.messenger.core.randomMessageIds
import io.slychat.messenger.core.randomUserConversationId
import io.slychat.messenger.services.messaging.MessageService
import io.slychat.messenger.services.messaging.MessengerService
import io.slychat.messenger.testutils.thenResolveUnit
import org.junit.Before
import org.junit.Test
import rx.subjects.PublishSubject

class MessageReadWatcherImplTest {
    private val messageService: MessageService = mock()
    private val messengerService: MessengerService = mock()

    private val messageUpdateEvents: PublishSubject<MessageUpdateEvent> = PublishSubject.create()

    @Before
    fun before() {
        whenever(messageService.messageUpdates).thenReturn(messageUpdateEvents)

        whenever(messengerService.broadcastMessagesRead(any(), any())).thenResolveUnit()
    }

    private fun createWatcher(): MessageReadWatcherImpl {
        return MessageReadWatcherImpl(messageService, messengerService)
    }

    private fun testBroadcastCall(fromSync: Boolean) {
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
        testBroadcastCall(false)
    }

    @Test
    fun `it should not call broadcastMessagesRead when a Read(fromSync=true) event is received`() {
        testBroadcastCall(false)
    }
}