package io.slychat.messenger.services

import com.nhaarman.mockito_kotlin.*
import io.slychat.messenger.core.UserId
import io.slychat.messenger.core.currentTimestamp
import io.slychat.messenger.core.persistence.AllowedMessageLevel
import io.slychat.messenger.core.persistence.ContactInfo
import io.slychat.messenger.core.persistence.ContactsPersistenceManager
import io.slychat.messenger.core.persistence.MessageInfo
import io.slychat.messenger.core.randomUUID
import io.slychat.messenger.services.config.UserConfig
import io.slychat.messenger.services.config.UserConfigService
import io.slychat.messenger.services.contacts.ContactDisplayInfo
import io.slychat.messenger.services.contacts.toContactDisplayInfo
import io.slychat.messenger.services.messaging.MessageBundle
import io.slychat.messenger.services.messaging.MessengerService
import io.slychat.messenger.services.ui.UIEventService
import io.slychat.messenger.testutils.KovenantTestModeRule
import nl.komponents.kovenant.Promise
import org.junit.Before
import org.junit.ClassRule
import org.junit.Test
import rx.subjects.PublishSubject
import kotlin.test.assertFalse

@Suppress("UNUSED_VARIABLE")
class NotifierServiceTest {
    companion object {
        @JvmField
        @ClassRule
        val kovenantTestMode = KovenantTestModeRule()
    }

    lateinit var uiEventService: UIEventService
    lateinit var messengerService: MessengerService
    lateinit var contactsPersistenceManager: ContactsPersistenceManager
    lateinit var platformNotificationsService: PlatformNotificationService
    lateinit var userConfigService: UserConfigService

    val newMessagesSubject: PublishSubject<MessageBundle> = PublishSubject.create()
    val uiEventSubject: PublishSubject<UIEvent> = PublishSubject.create()

    @Before
    fun before() {
        uiEventService = mock<UIEventService>()
        messengerService = mock<MessengerService>()
        contactsPersistenceManager = mock<ContactsPersistenceManager>()
        platformNotificationsService = mock<PlatformNotificationService>()

        whenever(messengerService.newMessages).thenReturn(newMessagesSubject)
        whenever(uiEventService.events).thenReturn(uiEventSubject)
    }

    fun initNotifierService(config: UserConfig = UserConfig()): NotifierService {
        userConfigService = UserConfigService(mock(), config = config)

        val notifierService = NotifierService(
            messengerService,
            uiEventService,
            contactsPersistenceManager,
            platformNotificationsService,
            userConfigService
        )

        notifierService.init()

        return notifierService
    }

    @Test
    fun `it should clear all notifications when the contacts page is visited`() {
        val notifierService = initNotifierService()

        val pageChangeEvent = PageChangeEvent(PageType.CONTACTS, "")
        uiEventSubject.onNext(pageChangeEvent)

        verify(platformNotificationsService, times(1)).clearAllMessageNotifications()
    }

    @Test
    fun `it should clear only notifications for the user when a convo is visited`() {
        val notifierService = initNotifierService()

        val userId = UserId(1)
        val email = "email"
        val name = "name"
        val contactInfo = ContactInfo(userId, email, name, AllowedMessageLevel.ALL, "", "")
        val contactDisplayInfo = ContactDisplayInfo(userId, name)
        whenever(contactsPersistenceManager.get(userId)).thenReturn(Promise.ofSuccess(contactInfo))

        val pageChangeEvent = PageChangeEvent(PageType.CONVO, userId.long.toString())
        uiEventSubject.onNext(pageChangeEvent)

        verify(platformNotificationsService, times(1)).clearMessageNotificationsForUser(contactDisplayInfo)
    }

    fun setupContactInfo(id: Long): ContactInfo {
        val userId = UserId(id)
        val contactInfo = ContactInfo(userId, "email", "name", AllowedMessageLevel.ALL, "", "")
        whenever(contactsPersistenceManager.get(userId)).thenReturn(Promise.ofSuccess(contactInfo))
        return contactInfo
    }

    fun testNotificationDisplay(shouldShow: Boolean) {
        val contactInfo = setupContactInfo(1)

        val messages = (0..1).map {
            MessageInfo(randomUUID(), it.toString(), currentTimestamp(), currentTimestamp(), false, true, 0)
        }

        val messageBundle = MessageBundle(contactInfo.id, messages)

        val contactDisplayInfo = contactInfo.toContactDisplayInfo()
        val lastMessage = messageBundle.messages.last()
        val messageCount = messageBundle.messages.size

        newMessagesSubject.onNext(messageBundle)

        if (shouldShow)
            verify(platformNotificationsService).addNewMessageNotification(contactDisplayInfo, lastMessage, messageCount)
        else
            verify(platformNotificationsService, never()).addNewMessageNotification(any(), any(), any())
    }

    @Test
    fun `it should show notifications when notifications are enabled and the ui is not visible`() {
        val notifierService = initNotifierService(UserConfig(notificationsEnabled = true))

        notifierService.isUiVisible = false

        testNotificationDisplay(true)
    }

    @Test
    fun `it should not show notifications when notifications are disabled and the ui is not visible`() {
        val notifierService = initNotifierService(UserConfig(notificationsEnabled = false))

        notifierService.isUiVisible = false

        testNotificationDisplay(false)
    }

    @Test
    fun `it should not show notifications for the currently open user page`() {
        val notifierService = initNotifierService(UserConfig(notificationsEnabled = true))

        notifierService.isUiVisible = true

        setupContactInfo(1)

        uiEventSubject.onNext(PageChangeEvent(PageType.CONVO, "1"))

        testNotificationDisplay(false)
    }

    @Test
    fun `it should show notifications for an unfocused user`() {
        val notifierService = initNotifierService(UserConfig(notificationsEnabled = true))

        notifierService.isUiVisible = true

        setupContactInfo(2)

        uiEventSubject.onNext(PageChangeEvent(PageType.CONVO, "2"))

        testNotificationDisplay(true)
    }

    @Test
    fun `it should not show notifications when the contact page page is focused`() {
        val notifierService = initNotifierService(UserConfig(notificationsEnabled = true))

        notifierService.isUiVisible = true

        uiEventSubject.onNext(PageChangeEvent(PageType.CONTACTS, ""))

        testNotificationDisplay(false)
    }

    @Test
    fun `it should update notifications enabled when receiving config update events`() {
        val notifierService = initNotifierService(UserConfig(notificationsEnabled = true))

        userConfigService.withEditor { notificationsEnabled = false }

        assertFalse(notifierService.enableNotificationDisplay, "Config change not reflected")
    }
}

