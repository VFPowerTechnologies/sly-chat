package io.slychat.messenger.services.messaging

import com.nhaarman.mockito_kotlin.*
import io.slychat.messenger.core.crypto.randomMessageId
import io.slychat.messenger.core.currentTimestamp
import io.slychat.messenger.core.persistence.ConversationId
import io.slychat.messenger.core.persistence.ExpiringMessage
import io.slychat.messenger.core.persistence.MessageInfo
import io.slychat.messenger.core.randomMessageText
import io.slychat.messenger.core.randomReceivedMessageInfo
import io.slychat.messenger.core.randomUserConversationId
import io.slychat.messenger.services.MessageUpdateEvent
import io.slychat.messenger.testutils.KovenantTestModeRule
import io.slychat.messenger.testutils.thenResolve
import io.slychat.messenger.testutils.thenResolveUnit
import io.slychat.messenger.testutils.withTimeAs
import org.junit.Before
import org.junit.ClassRule
import org.junit.Test
import rx.Observable
import rx.schedulers.TestScheduler
import rx.subjects.PublishSubject
import java.util.concurrent.TimeUnit
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class MessageExpirationWatcherImplTest {
    companion object {
        @ClassRule
        @JvmField
        val kovenantRule = KovenantTestModeRule()
    }

    val testScheduler = TestScheduler()

    val messageUpdates: PublishSubject<MessageUpdateEvent> = PublishSubject.create()

    val messageService: MessageService = mock()

    @Before
    fun before() {
        whenever(messageService.messageUpdates).thenReturn(messageUpdates)
        whenever(messageService.expireMessages(any())).thenResolveUnit()
        whenever(messageService.getMessagesAwaitingExpiration()).thenResolve(emptyList())
    }

    fun randomExpiringReceivedMessageInfo(expiresAt: Long, ttl: Long = 500): MessageInfo =
        MessageInfo(randomMessageId(), randomMessageText(), currentTimestamp(), currentTimestamp(), false, true, false, false, ttl, expiresAt)

    fun createWatcher(): MessageExpirationWatcherImpl {
        val rxTimerFactory = RxTimerFactory(testScheduler)
        return MessageExpirationWatcherImpl(testScheduler, rxTimerFactory, messageService)
    }

    @Test
    fun `it should fetch all currently expiring messages on startup`() {
        val watcher = createWatcher()

        watcher.init()

        verify(messageService).getMessagesAwaitingExpiration()
    }

    @Test
    fun `it should destroy all expired messages on startup`() {
        val watcher = createWatcher()

        val currentTime = currentTimestamp()

        val messageInfo = randomReceivedMessageInfo().copy(expiresAt = currentTime - 1)
        val conversationId = randomUserConversationId()

        val messages = listOf(
            ExpiringMessage(conversationId, messageInfo.id, messageInfo.expiresAt)
        )

        val expected = mapOf<ConversationId, Collection<String>>(
            conversationId to listOf(messageInfo.id)
        )

        whenever(messageService.getMessagesAwaitingExpiration()).thenResolve(messages)

        withTimeAs(currentTime) {
            watcher.init()
        }

        verify(messageService).expireMessages(expected)
    }

    @Test
    fun `it should expire a message picked up an Expiring event when its timeout expires`() {
        val baseTime = 1L
        val ttl = 1L
        val expiresAt = baseTime + ttl

        val watcher = createWatcher()

        val conversationId = randomUserConversationId()
        val messageId = randomMessageId()
        val expiring = MessageUpdateEvent.Expiring(conversationId, messageId, ttl, expiresAt)

        messageUpdates.onNext(expiring)

        withTimeAs(expiresAt) {
            testScheduler.advanceTimeTo(expiresAt, TimeUnit.MILLISECONDS)
        }

        val expected = mapOf<ConversationId, Collection<String>>(
            conversationId to listOf(messageId)
        )

        verify(messageService).expireMessages(expected)
    }

    @Test
    fun `it should remove an existing expiring entry from its list when receiving an Expired message update`() {
        val baseTime = 1L

        val watcher = createWatcher()

        val expiresAt = baseTime + 1
        val messageInfo = randomExpiringReceivedMessageInfo(expiresAt)

        val conversationId = randomUserConversationId()

        val messages = listOf(
            ExpiringMessage(conversationId, messageInfo.id, messageInfo.expiresAt)
        )

        whenever(messageService.getMessagesAwaitingExpiration()).thenResolve(messages)

        withTimeAs(baseTime) {
            watcher.init()
        }

        messageUpdates.onNext(MessageUpdateEvent.Expired(conversationId, messageInfo.id))

        withTimeAs(expiresAt) {
            testScheduler.advanceTimeTo(expiresAt, TimeUnit.MILLISECONDS)
        }

        verify(messageService, never()).expireMessages(any())
    }

    @Test
    fun `it should expire a message retrieved during initialization when its timeout has already expired`() {
        val baseTime = 1L

        val expiringMessageInfo = randomExpiringReceivedMessageInfo(baseTime - 1)
        val expiringConversationId = randomUserConversationId()

        val notExpiredMessageInfo = randomExpiringReceivedMessageInfo(baseTime + 1)

        val messages = listOf(
            ExpiringMessage(expiringConversationId, expiringMessageInfo.id, expiringMessageInfo.expiresAt),
            ExpiringMessage(randomUserConversationId(), notExpiredMessageInfo.id, notExpiredMessageInfo.expiresAt)
        )

        whenever(messageService.getMessagesAwaitingExpiration()).thenResolve(messages)

        val watcher = createWatcher()

        withTimeAs(baseTime) {
            watcher.init()
        }

        val destroyedMessages = mapOf<ConversationId, List<String>>(
            expiringConversationId to listOf(expiringMessageInfo.id)
        )

        verify(messageService).expireMessages(destroyedMessages)
    }

    @Test
    fun `it should expire messages retrieved during initialization`() {
        val baseTime = 1L

        val conversationId = randomUserConversationId()
        val expiresAt = baseTime + 1
        val messageInfo = randomExpiringReceivedMessageInfo(expiresAt)

        val messages = listOf(
            ExpiringMessage(conversationId, messageInfo.id, messageInfo.expiresAt)
        )

        whenever(messageService.getMessagesAwaitingExpiration()).thenResolve(messages)

        val watcher = createWatcher()

        withTimeAs(baseTime) {
            watcher.init()
        }

        withTimeAs(expiresAt) {
            testScheduler.advanceTimeTo(expiresAt, TimeUnit.MILLISECONDS)
        }

        val destroyedMessages = mapOf<ConversationId, List<String>>(
            conversationId to listOf(messageInfo.id)
        )

        verify(messageService).expireMessages(destroyedMessages)
    }

    @Test
    fun `it should not call destroyMessages on init if no messages need to be destroyed`() {
        whenever(messageService.getMessagesAwaitingExpiration()).thenResolve(emptyList())

        val watcher = createWatcher()

        watcher.init()

        verify(messageService, never()).expireMessages(any())
    }

    @Test
    fun `it should update the timer after expiring a message`() {
        val watcher = createWatcher()

        val baseTime = 1L

        val expiresAt = baseTime + 1
        val expiresAt2 = baseTime + 2

        val messageInfo = randomExpiringReceivedMessageInfo(expiresAt)
        val messageInfo2 = randomExpiringReceivedMessageInfo(expiresAt2)

        val conversationId = randomUserConversationId()
        val conversationId2 = randomUserConversationId()

        val messages = listOf(
            ExpiringMessage(conversationId, messageInfo.id, messageInfo.expiresAt),
            ExpiringMessage(conversationId2, messageInfo2.id, messageInfo2.expiresAt)
        )

        whenever(messageService.getMessagesAwaitingExpiration()).thenResolve(messages)

        withTimeAs(baseTime) {
            watcher.init()
        }

        withTimeAs(expiresAt) {
            testScheduler.advanceTimeTo(expiresAt, TimeUnit.MILLISECONDS)
        }

        withTimeAs(expiresAt2) {
            testScheduler.advanceTimeTo(expiresAt2, TimeUnit.MILLISECONDS)
        }

        verify(messageService).expireMessages(mapOf(
            conversationId2 to listOf(messageInfo2.id)
        ))
    }

    @Test
    fun `it should update the timer after reviving an Expired message if the item was removed`() {
        val watcher = createWatcher()

        val baseTime = 1L

        val expiresAt = baseTime + 1
        val expiresAt2 = baseTime + 2

        val messageInfo = randomExpiringReceivedMessageInfo(expiresAt)
        val messageInfo2 = randomExpiringReceivedMessageInfo(expiresAt2)

        val conversationId = randomUserConversationId()
        val conversationId2 = randomUserConversationId()

        val messages = listOf(
            ExpiringMessage(conversationId, messageInfo.id, messageInfo.expiresAt),
            ExpiringMessage(conversationId2, messageInfo2.id, messageInfo2.expiresAt)
        )

        whenever(messageService.getMessagesAwaitingExpiration()).thenResolve(messages)

        withTimeAs(baseTime) {
            watcher.init()
        }

        messageUpdates.onNext(MessageUpdateEvent.Expired(conversationId, messageInfo.id))

        withTimeAs(expiresAt2) {
            testScheduler.advanceTimeTo(expiresAt2, TimeUnit.MILLISECONDS)
        }

        verify(messageService).expireMessages(mapOf(
            conversationId2 to listOf(messageInfo2.id)
        ))
    }

    @Test
    fun `it should cancel any pending timer on shutdown`() {
        val baseTime = 1L
        val messageInfo = randomExpiringReceivedMessageInfo(baseTime + 1)

        val messages = listOf(
            ExpiringMessage(randomUserConversationId(), messageInfo.id, messageInfo.expiresAt)
        )

        whenever(messageService.getMessagesAwaitingExpiration()).thenResolve(messages)

        val testRxTimerFactory = object : RxTimerFactory(testScheduler) {
            var nTimers: Int = 0
                private set

            var unsubscribed: Boolean = false
                private set

            override fun createTimer(delay: Long, timeUnit: TimeUnit): Observable<Long> {
                val o = super.createTimer(delay, timeUnit)

                ++nTimers

                return o.doOnUnsubscribe { unsubscribed = true }
            }
        }

        val watcher = MessageExpirationWatcherImpl(testScheduler, testRxTimerFactory, messageService)

        withTimeAs(baseTime) {
            watcher.init()
        }

        watcher.shutdown()

        assertEquals(1, testRxTimerFactory.nTimers, "Only expected one timer to be created")
        assertTrue(testRxTimerFactory.unsubscribed, "Didn't unsubscribe from the timer")
    }
}