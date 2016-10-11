package io.slychat.messenger.services.ui.impl

import com.nhaarman.mockito_kotlin.whenever
import io.slychat.messenger.core.persistence.ConversationDisplayInfo
import io.slychat.messenger.services.MessageUpdateEvent
import io.slychat.messenger.services.MockUserComponent
import io.slychat.messenger.services.di.UserComponent
import io.slychat.messenger.services.messaging.ConversationMessage
import org.junit.Before
import org.junit.Test
import rx.subjects.PublishSubject
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class UIMessengerServiceImplTest {
    val userComponent = MockUserComponent()

    val messageUpdates: PublishSubject<MessageUpdateEvent> = PublishSubject.create()
    val conversationInfoUpdates: PublishSubject<ConversationDisplayInfo> = PublishSubject.create()
    val newMessages: PublishSubject<ConversationMessage> = PublishSubject.create()
    val clockDiffUpdates: PublishSubject<Long> = PublishSubject.create()

    @Before
    fun before() {
        whenever(userComponent.messageService.messageUpdates).thenReturn(messageUpdates)
        whenever(userComponent.messageService.conversationInfoUpdates).thenReturn(conversationInfoUpdates)
        whenever(userComponent.messageService.newMessages).thenReturn(newMessages)
        whenever(userComponent.relayClock.clockDiffUpdates).thenReturn(clockDiffUpdates)
    }

    @Test
    fun `addClockDifferenceUpdateListener should immediately call the listener with the cached value`() {
        val userSessionAvailable = PublishSubject.create<UserComponent?>()
        val uiMessengerService = UIMessengerServiceImpl(userSessionAvailable)

        userSessionAvailable.onNext(userComponent)

        var wasCalled = false
        uiMessengerService.addClockDifferenceUpdateListener {
            wasCalled = true

            assertEquals(0, it, "Invalid diff")
        }

        assertTrue(wasCalled, "Listener not called")
    }
}