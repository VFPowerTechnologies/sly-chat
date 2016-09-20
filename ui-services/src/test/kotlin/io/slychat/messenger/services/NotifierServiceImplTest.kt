package io.slychat.messenger.services

import com.nhaarman.mockito_kotlin.*
import io.slychat.messenger.core.*
import io.slychat.messenger.core.crypto.randomMessageId
import io.slychat.messenger.core.persistence.*
import io.slychat.messenger.services.config.UserConfig
import io.slychat.messenger.services.config.UserConfigService
import io.slychat.messenger.services.messaging.MessageBundle
import io.slychat.messenger.testutils.KovenantTestModeRule
import io.slychat.messenger.testutils.thenResolve
import nl.komponents.kovenant.Promise
import org.assertj.core.api.Assertions
import org.junit.Before
import org.junit.ClassRule
import org.junit.Test
import rx.schedulers.TestScheduler
import rx.subjects.BehaviorSubject
import rx.subjects.PublishSubject
import java.util.concurrent.TimeUnit
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@Suppress("UNUSED_VARIABLE")
class NotifierServiceImplTest {
    companion object {
        @JvmField
        @ClassRule
        val kovenantTestMode = KovenantTestModeRule()

        init {
            MockitoKotlin.registerInstanceCreator { randomGroupId() }
        }
    }

    val contactsPersistenceManager: ContactsPersistenceManager = mock()
    val groupPersistenceManager: GroupPersistenceManager = mock()
    val platformNotificationsService: PlatformNotificationService = mock()
    lateinit var userConfigService: UserConfigService

    val uiEventSubject: PublishSubject<UIEvent> = PublishSubject.create()
    val uiVisibility: BehaviorSubject<Boolean> = BehaviorSubject.create()
    val conversationInfoUpdates: PublishSubject<ConversationDisplayInfo> = PublishSubject.create()

    val testScheduler = TestScheduler()

    @Before
    fun before() {
        whenever(groupPersistenceManager.getInfo(any())).thenResolve(null)
    }

    fun createNotifierService(isUiVisible: Boolean = false, config: UserConfig = UserConfig(notificationsEnabled = true), bufferMs: Long = 0): NotifierServiceImpl {
        userConfigService = UserConfigService(mock(), config = config)

        uiVisibility.onNext(isUiVisible)

        val notifierService = NotifierServiceImpl(
            uiEventSubject,
            conversationInfoUpdates,
            uiVisibility,
            testScheduler,
            bufferMs,
            platformNotificationsService,
            userConfigService
        )

        notifierService.init()

        return notifierService
    }

    @Test
    fun `it should clear all notifications when the contacts page is visited`() {
        val notifierService = createNotifierService()

        val pageChangeEvent = UIEvent.PageChange(PageType.CONTACTS, "")
        uiEventSubject.onNext(pageChangeEvent)

        verify(platformNotificationsService, times(1)).updateNotificationState(NotificationState.empty)
    }

    fun setupContactInfo(id: Long): ContactInfo {
        val userId = UserId(id)
        val contactInfo = ContactInfo(userId, "email", "name", AllowedMessageLevel.ALL, "", "")
        whenever(contactsPersistenceManager.get(userId)).thenReturn(Promise.ofSuccess(contactInfo))
        return contactInfo
    }

    fun testConvoNotificationDisplay(shouldShow: Boolean, isRead: Boolean = false) {
        val contactInfo = setupContactInfo(1)

        val messages = (0..1).map {
            randomReceivedMessageInfo().copy(isRead = isRead)
        }

        val messageBundle = MessageBundle(contactInfo.id, null, messages)

        val lastMessage = messageBundle.messages.last()
        val messageCount = messageBundle.messages.size

        val conversationDisplayInfo = randomConversationDisplayInfo()
        conversationInfoUpdates.onNext(conversationDisplayInfo)

        val state = NotificationState(listOf(NotificationConversationInfo(conversationDisplayInfo, true)))

        if (shouldShow)
            verify(platformNotificationsService).updateNotificationState(state)
        else
            //need to allow for empty state setting
            verify(platformNotificationsService, never()).updateNotificationState(state)
    }

    @Test
    fun `it should show notifications when notifications are enabled and the ui is not visible`() {
        val notifierService = createNotifierService(isUiVisible = false)

        testConvoNotificationDisplay(true)
    }

    @Test
    fun `it should not show notifications when notifications are disabled and the ui is not visible`() {
        val notifierService = createNotifierService(isUiVisible = false, config = UserConfig(notificationsEnabled = false))

        testConvoNotificationDisplay(false)
    }

    @Test
    fun `it should show notifications when the contacts page is not focused`() {
        val notifierService = createNotifierService(isUiVisible = true)

        setupContactInfo(2)

        uiEventSubject.onNext(UIEvent.PageChange(PageType.CONVO, "2"))

        testConvoNotificationDisplay(true)
    }

    @Test
    fun `it should not show notifications when the contact page page is focused`() {
        val notifierService = createNotifierService(isUiVisible = true)

        uiEventSubject.onNext(UIEvent.PageChange(PageType.CONTACTS, ""))

        testConvoNotificationDisplay(false)
    }

    @Test
    fun `hiding the ui after visiting the contacts page should show notifications`() {
        val notifierService = createNotifierService(isUiVisible = true)

        uiEventSubject.onNext(UIEvent.PageChange(PageType.CONTACTS, ""))
        uiVisibility.onNext(false)

        testConvoNotificationDisplay(true)
    }

    @Test
    fun `restoring the ui after having visited the contacts page should not show notifications`() {
        val notifierService = createNotifierService(isUiVisible = true)

        uiEventSubject.onNext(UIEvent.PageChange(PageType.CONTACTS, ""))
        uiVisibility.onNext(false)
        uiVisibility.onNext(true)

        testConvoNotificationDisplay(false)
    }

    @Test
    fun `it should clear notifications when restoring ui if the previous page is the contacts page`() {
        val notifierService = createNotifierService(isUiVisible = true)

        uiEventSubject.onNext(UIEvent.PageChange(PageType.CONTACTS, ""))
        uiVisibility.onNext(false)
        uiVisibility.onNext(true)

        verify(platformNotificationsService, times(2)).updateNotificationState(NotificationState.empty)
    }

    @Test
    fun `it should update notifications enabled when receiving config update events`() {
        val notifierService = createNotifierService()

        userConfigService.withEditor { notificationsEnabled = false }

        assertFalse(notifierService.enableNotificationDisplay, "Config change not reflected")
    }

    @Test
    fun `it should unsubscribe from UI events on shutdown`() {
        uiVisibility.onNext(false)

        var hasUnsubscribed = false

        val notifierService = NotifierServiceImpl(
            uiEventSubject.doOnUnsubscribe { hasUnsubscribed = true },
            conversationInfoUpdates,
            uiVisibility,
            testScheduler,
            0,
            platformNotificationsService,
            UserConfigService(mock())
        )

        notifierService.init()

        notifierService.shutdown()

        assertTrue(hasUnsubscribed, "Must unsubscribe from UI events on shutdown")
    }

    @Test
    fun `it should properly buffer ConversationDisplayInfo`() {
        val bufferMs = 10L

        val notifierService = createNotifierService(bufferMs = bufferMs)

        val conversationDisplayInfo = randomConversationDisplayInfo()
        conversationInfoUpdates.onNext(conversationDisplayInfo)

        verify(platformNotificationsService, never()).updateNotificationState(any())

        testScheduler.advanceTimeBy(bufferMs, TimeUnit.MILLISECONDS)

        verify(platformNotificationsService).updateNotificationState(any())
    }

    private fun generateConversationDisplayInfo(conversationId: ConversationId, vararg messageIds: String): ConversationDisplayInfo {
        return ConversationDisplayInfo(
            conversationId,
            null,
            messageIds.size,
            messageIds.toList(),
            randomLastMessageData()
        )
    }

    private fun testMergeNotificationConversationInfo(expectedHasNew: Boolean, body: (ConversationId) -> Pair<Set<String>, List<ConversationDisplayInfo>>) {
        val conversationId: ConversationId = randomUserConversationId()
        val (previousMessageIds, inputs) = body(conversationId)

        val previousState = mapOf(
            conversationId to NotificationConversationInfo(
                ConversationDisplayInfo(
                    conversationId,
                    null,
                    previousMessageIds.size,
                    previousMessageIds.toList(),
                    randomLastMessageData()
                ),
                //this value doesn't matter
                false
            )
        )

        val output = NotifierServiceImpl.mergeNotificationConversationInfo(previousState, inputs)

        val expected = mapOf(
            conversationId to NotificationConversationInfo(inputs.last(), expectedHasNew)
        )
    }

    @Test
    fun `mergeNotificationConversationInfo should set hasNew=true if new messages are present`() {
        testMergeNotificationConversationInfo(true) { conversationId ->
            val previousState = setOf(randomMessageId())

            val inputs = listOf(generateConversationDisplayInfo(conversationId, randomMessageId()))

            previousState to inputs
        }
    }

    @Test
    fun `mergeNotificationConversationInfo should set hasNew=false if no new messages are present`() {
        testMergeNotificationConversationInfo(false) { conversationId ->
            val messageId = randomMessageId()
            val previousState = setOf(messageId)

            val inputs = listOf(generateConversationDisplayInfo(conversationId, messageId))

            previousState to inputs
        }
    }

    @Test
    fun `mergeNotificationConversationInfo should set hasNew=false if no messages are present as the last input and not previous state is available`() {
        testMergeNotificationConversationInfo(false) {
            val inputs = listOf(generateConversationDisplayInfo(it))

            emptySet<String>() to inputs
        }
    }

    @Test
    fun `mergeNotificationConversationInfo should set hasNew=false if no messages are present as the last input and a previous state is available`() {
        testMergeNotificationConversationInfo(false) { conversationId ->
            val previousState = setOf(randomMessageId())

            val inputs = listOf(
                generateConversationDisplayInfo(conversationId, randomMessageId()),
                generateConversationDisplayInfo(conversationId)
            )

            previousState to inputs
        }
    }

    @Test
    fun `mergeNotificationConversationInfo should set hasNew=false for previous conversation data`() {
        val conversationId: ConversationId = randomUserConversationId()

        val conversationDisplayInfo = ConversationDisplayInfo(
            conversationId,
            null,
            1,
            randomMessageIds(1),
            randomLastMessageData()
        )

        val notificationConversationInfo = NotificationConversationInfo(
            conversationDisplayInfo,
            true
        )

        val expectedNotificationConversationInfo = NotificationConversationInfo(
            conversationDisplayInfo,
            false
        )

        val previousState = mapOf(
            conversationId to notificationConversationInfo
        )

        val inputs = listOf(
            randomConversationDisplayInfo()
        )

        val output = NotifierServiceImpl.mergeNotificationConversationInfo(previousState, inputs)

        Assertions.assertThat(output).apply {
            `as`("It should update hasNew for older entries")
            containsEntry(conversationId, expectedNotificationConversationInfo)
        }
    }

    @Test
    fun `mergeNotificationConversationInfo should remove entries with no unread messages`() {
        val conversationId: ConversationId = randomUserConversationId()

        val notificationConversationInfo = NotificationConversationInfo(
            ConversationDisplayInfo(
                conversationId,
                null,
                1,
                randomMessageIds(1),
                randomLastMessageData()
            ),
            false
        )

        val previousState = mapOf(
            conversationId to notificationConversationInfo
        )

        val inputs = listOf(
            notificationConversationInfo.conversationDisplayInfo.copy(unreadCount = 0, latestUnreadMessageIds = emptyList())
        )

        val output = NotifierServiceImpl.mergeNotificationConversationInfo(previousState, inputs)

        Assertions.assertThat(output).apply {
            `as`("It should remove empty entries")
            doesNotContainKey(conversationId)
        }
    }
}

