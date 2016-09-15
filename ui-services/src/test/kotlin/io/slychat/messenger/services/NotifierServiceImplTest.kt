package io.slychat.messenger.services

import com.nhaarman.mockito_kotlin.*
import io.slychat.messenger.core.UserId
import io.slychat.messenger.core.persistence.*
import io.slychat.messenger.core.randomConversationDisplayInfo
import io.slychat.messenger.core.randomGroupId
import io.slychat.messenger.core.randomReceivedMessageInfo
import io.slychat.messenger.services.config.UserConfig
import io.slychat.messenger.services.config.UserConfigService
import io.slychat.messenger.services.messaging.MessageBundle
import io.slychat.messenger.testutils.KovenantTestModeRule
import io.slychat.messenger.testutils.thenResolve
import nl.komponents.kovenant.Promise
import org.junit.Before
import org.junit.ClassRule
import org.junit.Test
import rx.subjects.BehaviorSubject
import rx.subjects.PublishSubject
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

    @Before
    fun before() {
        whenever(groupPersistenceManager.getInfo(any())).thenResolve(null)
    }

    fun initNotifierService(isUiVisible: Boolean = false, config: UserConfig = UserConfig(notificationsEnabled = true)): NotifierServiceImpl {
        userConfigService = UserConfigService(mock(), config = config)

        uiVisibility.onNext(isUiVisible)

        val notifierService = NotifierServiceImpl(
            uiEventSubject,
            conversationInfoUpdates,
            uiVisibility,
            platformNotificationsService,
            userConfigService
        )

        notifierService.init()

        return notifierService
    }

    @Test
    fun `it should clear all notifications when the contacts page is visited`() {
        val notifierService = initNotifierService()

        val pageChangeEvent = UIEvent.PageChange(PageType.CONTACTS, "")
        uiEventSubject.onNext(pageChangeEvent)

        verify(platformNotificationsService, times(1)).clearAllMessageNotifications()
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

        if (shouldShow)
            verify(platformNotificationsService).updateConversationNotification(conversationDisplayInfo)
        else
            verify(platformNotificationsService, never()).updateConversationNotification(any())
    }

    @Test
    fun `it should show notifications when notifications are enabled and the ui is not visible`() {
        val notifierService = initNotifierService(isUiVisible = false)

        testConvoNotificationDisplay(true)
    }

    @Test
    fun `it should not show notifications when notifications are disabled and the ui is not visible`() {
        val notifierService = initNotifierService(isUiVisible = false, config = UserConfig(notificationsEnabled = false))

        testConvoNotificationDisplay(false)
    }

    @Test
    fun `it should show notifications when the contacts page is not focused`() {
        val notifierService = initNotifierService(isUiVisible = true)

        setupContactInfo(2)

        uiEventSubject.onNext(UIEvent.PageChange(PageType.CONVO, "2"))

        testConvoNotificationDisplay(true)
    }

    @Test
    fun `it should not show notifications when the contact page page is focused`() {
        val notifierService = initNotifierService(isUiVisible = true)

        uiEventSubject.onNext(UIEvent.PageChange(PageType.CONTACTS, ""))

        testConvoNotificationDisplay(false)
    }

    @Test
    fun `it should update notifications enabled when receiving config update events`() {
        val notifierService = initNotifierService()

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
            platformNotificationsService,
            UserConfigService(mock())
        )

        notifierService.init()

        notifierService.shutdown()

        assertTrue(hasUnsubscribed, "Must unsubscribe from UI events on shutdown")
    }
}

