package io.slychat.messenger.services.messaging

import com.nhaarman.mockito_kotlin.*
import io.slychat.messenger.core.*
import io.slychat.messenger.core.crypto.randomMessageId
import io.slychat.messenger.core.persistence.ConversationId
import io.slychat.messenger.core.persistence.ConversationMessageInfo
import io.slychat.messenger.core.persistence.MessageInfo
import io.slychat.messenger.core.persistence.MessagePersistenceManager
import io.slychat.messenger.services.MessageUpdateEvent
import io.slychat.messenger.services.assertEventEmitted
import io.slychat.messenger.services.assertNoEventsEmitted
import io.slychat.messenger.services.subclassFilterTestSubscriber
import io.slychat.messenger.testutils.*
import org.assertj.core.api.Assertions.assertThat
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
        whenever(messagePersistenceManager.addMessage(any(), any())).thenResolveUnit()
        whenever(messagePersistenceManager.markMessageAsDelivered(any(), any(), any())).thenResolve(null)
        whenever(messagePersistenceManager.markConversationAsRead(any())).thenResolveUnit()
        whenever(messagePersistenceManager.setExpiration(any(), any(), any())).thenResolveUnit()
        whenever(messagePersistenceManager.expireMessages(any())).thenResolveUnit()
    }

    fun forEachConvType(body: (ConversationId) -> Unit) {
        body(randomUserConversationId())
        body(randomGroupConversationId())
    }

    inline fun <reified T : MessageUpdateEvent> messageUpdateEventCollectorFor(): TestSubscriber<T> {
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

            val testSubscriber = messageUpdateEventCollectorFor<MessageUpdateEvent.Delivered>()

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

                assertThat(testSubscriber.onNextEvents).apply {
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

    @Test
    fun `it should emit an expiring event when startMessageExpiration is called for an existing message id`() {
        forEachConvType { conversationId ->
            val baseTime = 1L
            val ttl = 500L

            val conversationMessageInfo = ConversationMessageInfo(
                null,
                MessageInfo.Companion.newSent(randomMessageText(), ttl)
            )

            val messageId = conversationMessageInfo.info.id

            whenever(messagePersistenceManager.get(conversationId, messageId)).thenResolve(conversationMessageInfo)

            val testSubscriber = messageUpdateEventCollectorFor<MessageUpdateEvent.Expiring>()

            withTimeAs(baseTime) {
                messageService.startMessageExpiration(conversationId, messageId).get()
            }

            val expiresAt = baseTime + ttl

            verify(messagePersistenceManager).setExpiration(conversationId, messageId, expiresAt)

            val expectedEvent = MessageUpdateEvent.Expiring(conversationId, messageId, ttl, expiresAt)

            assertEventEmitted(testSubscriber) {
                assertEquals(expectedEvent, it, "Invalid event")
            }
        }
    }

    @Test
    fun `it should not emit an expiring event when startMessageExpiration is called for an invalid message id`() {
        forEachConvType { conversationId ->
            whenever(messagePersistenceManager.get(any(), any())).thenResolve(null)

            val testSubscriber = messageUpdateEventCollectorFor<MessageUpdateEvent.Expiring>()

            messageService.startMessageExpiration(conversationId, randomMessageId()).get()

            verify(messagePersistenceManager, never()).setExpiration(any(), any(), any())

            assertNoEventsEmitted(testSubscriber)
        }
    }

    @Test
    fun `it should emit expired events for messages when expireMessages is called`() {
        forEachConvType { conversationId ->
            val messageIds = (0..2).map { randomMessageId() }

            val messages = mapOf(
                conversationId to messageIds
            )

            val testSubscriber = messageUpdateEventCollectorFor<MessageUpdateEvent.Expired>()

            messageService.expireMessages(messages).get()

            val expected = messageIds.map {
                MessageUpdateEvent.Expired(conversationId, it)
            }

            assertThat(testSubscriber.onNextEvents).apply {
                `as`("Should emit events")
                containsOnlyElementsOf(expected)
            }
        }
    }
}