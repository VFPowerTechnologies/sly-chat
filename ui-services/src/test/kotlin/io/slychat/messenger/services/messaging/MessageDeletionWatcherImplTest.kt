package io.slychat.messenger.services.messaging

import com.nhaarman.mockito_kotlin.*
import io.slychat.messenger.core.randomMessageIds
import io.slychat.messenger.core.randomUserConversationId
import io.slychat.messenger.services.MessageUpdateEvent
import io.slychat.messenger.testutils.thenResolveUnit
import org.junit.Before
import org.junit.Test
import rx.subjects.PublishSubject

class MessageDeletionWatcherImplTest {
    private val messengerService: MessengerService = mock()
    private val messageUpdateEvents: PublishSubject<MessageUpdateEvent> = PublishSubject.create()
    private val watcher = MessageDeletionWatcherImpl(messageUpdateEvents, messengerService)

    @Before
    fun before() {
        whenever(messengerService.broadcastDeleted(any(), any())).thenResolveUnit()
        whenever(messengerService.broadcastDeletedAll(any(), any())).thenResolveUnit()
    }

    @Test
    fun `it should call broadcastDeleted when receiving Deleted(fromSync=false)`() {
        val conversationId = randomUserConversationId()
        val messageIds = randomMessageIds()

        messageUpdateEvents.onNext(MessageUpdateEvent.Deleted(conversationId, messageIds, false))

        verify(messengerService).broadcastDeleted(conversationId, messageIds)
    }

    @Test
    fun `it should ignore Deleted(fromSync=true)`() {
        messageUpdateEvents.onNext(MessageUpdateEvent.Deleted(randomUserConversationId(), randomMessageIds(), true))

        verify(messengerService, never()).broadcastDeleted(any(), any())
    }

    @Test
    fun `it should call broadcastDeletedAll when receiving DeletedAll(fromSync=false)`() {
        val conversationId = randomUserConversationId()
        val lastMessageTimestamp = 0L

        messageUpdateEvents.onNext(MessageUpdateEvent.DeletedAll(conversationId, lastMessageTimestamp, false))

        verify(messengerService).broadcastDeletedAll(conversationId, lastMessageTimestamp)
    }

    @Test
    fun `it should ignore DeletedAll(fromSync=true)`() {
        messageUpdateEvents.onNext(MessageUpdateEvent.DeletedAll(randomUserConversationId(), 0, true))

        verify(messengerService, never()).broadcastDeletedAll(any(), any())
    }
}
