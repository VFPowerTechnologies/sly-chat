package io.slychat.messenger.services

import com.nhaarman.mockito_kotlin.*
import io.slychat.messenger.core.persistence.ConversationId
import io.slychat.messenger.core.persistence.toConversationId
import io.slychat.messenger.core.randomGroupConversationId
import io.slychat.messenger.core.randomGroupId
import io.slychat.messenger.core.randomUserConversationId
import io.slychat.messenger.core.randomUserId
import io.slychat.messenger.services.messaging.MessageService
import io.slychat.messenger.testutils.KovenantTestModeRule
import io.slychat.messenger.testutils.thenResolve
import org.junit.Before
import org.junit.ClassRule
import org.junit.Test
import rx.subjects.BehaviorSubject
import rx.subjects.PublishSubject

class ConversationWatcherImplTest {
    companion object {
        @ClassRule
        @JvmField
        val kovenantRule = KovenantTestModeRule()
    }

    val uiVisibility: BehaviorSubject<Boolean> = BehaviorSubject.create()
    val uiEvents: PublishSubject<UIEvent> = PublishSubject.create()
    val messageService: MessageService = mock()

    val watcher = ConversationWatcherImpl(uiEvents, uiVisibility, messageService)

    @Before
    fun before() {
        whenever(messageService.markConversationAsRead(any())).thenResolve(Unit)
    }

    @Test
    fun `it should mark a user conversation as read when navigating to the user page`() {
        val userId = randomUserId()

        uiEvents.onNext(UIEvent.PageChange(PageType.CONVO, userId.toString()))

        verify(messageService).markConversationAsRead(userId.toConversationId())
    }

    @Test
    fun `it should mark a group conversation as read when navigating to the group page`() {
        val groupId = randomGroupId()

        uiEvents.onNext(UIEvent.PageChange(PageType.GROUP, groupId.toString()))

        verify(messageService).markConversationAsRead(groupId.toConversationId())
    }

    fun testUiRestore(conversationId: ConversationId) {
        val (pageType, extra) = when (conversationId) {
            is ConversationId.User -> PageType.CONVO to conversationId.id.toString()
            is ConversationId.Group -> PageType.GROUP to conversationId.id.toString()
        }

        uiEvents.onNext(UIEvent.PageChange(pageType, extra))
        reset(messageService)
        whenever(messageService.markConversationAsRead(conversationId)).thenResolve(Unit)

        uiVisibility.onNext(false)
        uiVisibility.onNext(true)

        verify(messageService).markConversationAsRead(conversationId)
    }

    @Test
    fun `it should mark a user conversation as read when restoring ui to a user page`() {
        testUiRestore(randomUserConversationId())
    }

    @Test
    fun `it should mark a group conversation as read when restoring ui to a group page`() {
        testUiRestore(randomGroupConversationId())
    }
}