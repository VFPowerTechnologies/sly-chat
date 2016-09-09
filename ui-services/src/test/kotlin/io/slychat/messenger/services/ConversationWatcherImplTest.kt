package io.slychat.messenger.services

import com.nhaarman.mockito_kotlin.any
import com.nhaarman.mockito_kotlin.mock
import com.nhaarman.mockito_kotlin.verify
import com.nhaarman.mockito_kotlin.whenever
import io.slychat.messenger.core.persistence.toConversationId
import io.slychat.messenger.core.randomGroupId
import io.slychat.messenger.core.randomUserId
import io.slychat.messenger.services.messaging.MessageService
import io.slychat.messenger.testutils.KovenantTestModeRule
import io.slychat.messenger.testutils.thenResolve
import org.junit.Before
import org.junit.ClassRule
import org.junit.Test
import rx.subjects.PublishSubject

class ConversationWatcherImplTest {
    companion object {
        @ClassRule
        @JvmField
        val kovenantRule = KovenantTestModeRule()
    }

    val uiEvents: PublishSubject<UIEvent> = PublishSubject.create()
    val messageService: MessageService = mock()

    val watcher = ConversationWatcherImpl(uiEvents, messageService)

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
}