package io.slychat.messenger.services.messaging

import com.nhaarman.mockito_kotlin.any
import com.nhaarman.mockito_kotlin.mock
import com.nhaarman.mockito_kotlin.verify
import com.nhaarman.mockito_kotlin.whenever
import io.slychat.messenger.core.*
import io.slychat.messenger.core.persistence.ConversationId
import io.slychat.messenger.core.persistence.MessagePersistenceManager
import io.slychat.messenger.services.MessageUpdateEvent
import io.slychat.messenger.services.assertEventEmitted
import io.slychat.messenger.services.assertNoEventsEmitted
import io.slychat.messenger.services.subclassFilterTestSubscriber
import io.slychat.messenger.testutils.KovenantTestModeRule
import io.slychat.messenger.testutils.testSubscriber
import io.slychat.messenger.testutils.thenResolve
import org.assertj.core.api.Assertions
import org.junit.Before
import org.junit.ClassRule
import org.junit.Ignore
import org.junit.Test
import rx.observers.TestSubscriber
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class MessageServiceImplTest {
    companion object {
        @JvmField
        @ClassRule
        val kovenantTestMode = KovenantTestModeRule()
    }

    val messagePersistenceManager: MessagePersistenceManager = mock()

    val messageService = MessageServiceImpl(messagePersistenceManager)

    @Before
    fun before() {
        whenever(messagePersistenceManager.addMessage(any(), any())).thenResolve(Unit)
        whenever(messagePersistenceManager.markMessageAsDelivered(any(), any(), any())).thenResolve(null)
        whenever(messagePersistenceManager.markConversationAsRead(any())).thenResolve(Unit)
    }

    fun forEachConvType(body: (ConversationId) -> Unit) {
        body(randomUserConversationId())
        body(randomGroupConversationId())
    }

    inline fun <reified T : MessageUpdateEvent> messageUpdateEventCollectorFor(messageService: MessageServiceImpl): TestSubscriber<T> {
        return messageService.messageUpdates.subclassFilterTestSubscriber()
    }

    fun testDelivery(isAlreadyDelivered: Boolean) {
        forEachConvType { conversationId ->
            val conversationInfo = randomSentConversationMessageInfo()
            val timestamp = currentTimestamp()

            val messageId = conversationInfo.info.id

            val stubbing = whenever(messagePersistenceManager.markMessageAsDelivered(conversationId, messageId, timestamp))

            if (!isAlreadyDelivered)
                stubbing.thenResolve(conversationInfo)
            else
                stubbing.thenResolve(null)

            val testSubscriber = messageUpdateEventCollectorFor<MessageUpdateEvent.Delivered>(messageService)

            val got = messageService.markMessageAsDelivered(conversationId, messageId, timestamp).get()

            if (!isAlreadyDelivered) {
                assertEventEmitted(testSubscriber) {
                    assertEquals(conversationId, it.conversationId, "Invalid conversation id")
                    assertEquals(messageId, it.messageId, "Invalid message id")
                }

                assertNotNull(got, "Message info should be returned")
            }
            else {
                assertNoEventsEmitted(testSubscriber)
                assertNull(got, "Message info should not be returned")
            }
        }
    }

    @Test
    fun `it should emit an update event when markMessageAsDelivered is called for an undelivered message`() {
        testDelivery(false)
    }

    @Test
    fun `it should not emit an update event when markMessageAsDelivered is called for an already delivered message`() {
        testDelivery(true)
    }

    fun testAddMessage(isDuplicate: Boolean) {
        forEachConvType { conversationId ->
            listOf(randomSentConversationMessageInfo(), randomReceivedConversationMessageInfo(randomUserId())).forEach { conversationMessageInfo ->
                val expected = when (conversationId) {
                    is ConversationId.User -> ConversationMessage.Single(conversationId.id, conversationMessageInfo.info)
                    is ConversationId.Group -> ConversationMessage.Group(conversationId.id, conversationMessageInfo.speaker, conversationMessageInfo.info)
                }

                val testSubscriber = messageService.newMessages.testSubscriber()

                messageService.addMessage(conversationId, conversationMessageInfo).get()

                Assertions.assertThat(testSubscriber.onNextEvents).apply {
                    `as`("Should emit an event")
                    containsOnly(expected)
                }
            }
        }
    }

    @Test
    fun `it should emit a new message event when addMessage is called with a new message`() {
        testAddMessage(false)
    }

    @Ignore("TODO")
    @Test
    fun `it should not emit a new message event when addMessage is called with a duplicate message`() { TODO() }

    @Test
    fun `it should mark the conversation as read when markConversationAsRead is called`() {
        forEachConvType {
            messageService.markConversationAsRead(it).get()

            verify(messagePersistenceManager).markConversationAsRead(it)
        }
    }
}