package io.slychat.messenger.services

import com.nhaarman.mockito_kotlin.*
import io.slychat.messenger.core.*
import io.slychat.messenger.core.persistence.*
import io.slychat.messenger.services.config.UserConfig
import io.slychat.messenger.services.config.UserConfigService
import io.slychat.messenger.services.contacts.NotificationConversationInfo
import io.slychat.messenger.services.contacts.NotificationMessageInfo
import io.slychat.messenger.services.messaging.MessageBundle
import io.slychat.messenger.services.messaging.MessengerService
import io.slychat.messenger.services.ui.UIEventService
import io.slychat.messenger.testutils.KovenantTestModeRule
import io.slychat.messenger.testutils.thenReturn
import io.slychat.messenger.testutils.thenReturnNull
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

    val uiEventService: UIEventService = mock()
    val messengerService: MessengerService = mock()
    val contactsPersistenceManager: ContactsPersistenceManager = mock()
    val groupPersistenceManager: GroupPersistenceManager = mock()
    val platformNotificationsService: PlatformNotificationService = mock()
    lateinit var userConfigService: UserConfigService

    val newMessagesSubject: PublishSubject<MessageBundle> = PublishSubject.create()
    val uiEventSubject: PublishSubject<UIEvent> = PublishSubject.create()

    @Before
    fun before() {
        whenever(messengerService.newMessages).thenReturn(newMessagesSubject)
        whenever(uiEventService.events).thenReturn(uiEventSubject)
        whenever(groupPersistenceManager.getInfo(any())).thenReturnNull()
    }

    fun initNotifierService(config: UserConfig = UserConfig(notificationsEnabled = true)): NotifierService {
        userConfigService = UserConfigService(mock(), config = config)

        val notifierService = NotifierService(
            messengerService,
            uiEventService,
            contactsPersistenceManager,
            groupPersistenceManager,
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
    fun `it should clear only notifications for the focused user when a convo is visited`() {
        val notifierService = initNotifierService()

        val userId = UserId(1)
        val email = "email"
        val name = "name"
        val contactInfo = ContactInfo(userId, email, name, AllowedMessageLevel.ALL, "", "")
        val conversationInfo = NotificationConversationInfo.from(contactInfo)
        whenever(contactsPersistenceManager.get(userId)).thenReturn(Promise.ofSuccess(contactInfo))

        val pageChangeEvent = PageChangeEvent(PageType.CONVO, userId.long.toString())
        uiEventSubject.onNext(pageChangeEvent)

        verify(platformNotificationsService, times(1)).clearMessageNotificationsFor(conversationInfo)
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

        val conversationInfo = NotificationConversationInfo.from(contactInfo)
        val lastMessage = messageBundle.messages.last()
        val messageCount = messageBundle.messages.size

        val messageInfo = NotificationMessageInfo(
            contactInfo.name,
            lastMessage.message,
            lastMessage.timestamp
        )

        newMessagesSubject.onNext(messageBundle)

        if (shouldShow)
            verify(platformNotificationsService).addNewMessageNotification(conversationInfo, messageInfo, messageCount)
        else
            verify(platformNotificationsService, never()).addNewMessageNotification(any(), any(), any())
    }

    @Test
    fun `it should show notifications when notifications are enabled and the ui is not visible`() {
        val notifierService = initNotifierService()

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
        val notifierService = initNotifierService()

        notifierService.isUiVisible = true

        setupContactInfo(1)

        uiEventSubject.onNext(PageChangeEvent(PageType.CONVO, "1"))

        testNotificationDisplay(false)
    }

    @Test
    fun `it should show notifications for an unfocused user`() {
        val notifierService = initNotifierService()

        notifierService.isUiVisible = true

        setupContactInfo(2)

        uiEventSubject.onNext(PageChangeEvent(PageType.CONVO, "2"))

        testNotificationDisplay(true)
    }

    @Test
    fun `it should not show notifications when the contact page page is focused`() {
        val notifierService = initNotifierService()

        notifierService.isUiVisible = true

        uiEventSubject.onNext(PageChangeEvent(PageType.CONTACTS, ""))

        testNotificationDisplay(false)
    }

    @Test
    fun `it should update notifications enabled when receiving config update events`() {
        val notifierService = initNotifierService()

        userConfigService.withEditor { notificationsEnabled = false }

        assertFalse(notifierService.enableNotificationDisplay, "Config change not reflected")
    }

    fun testGroupMessageBundle(body: (ContactInfo, GroupInfo, NotificationMessageInfo) -> Unit) {
        val notifierService = initNotifierService()

        val groupInfo = randomGroupInfo()
        val groupId = groupInfo.id

        val contactInfo = randomContactInfo(AllowedMessageLevel.ALL)
        val userId = contactInfo.id

        val messageInfo = randomReceivedMessageInfo()
        val bundle = MessageBundle(
            userId,
            groupId,
            listOf(messageInfo)
        )

        whenever(contactsPersistenceManager.get(userId)).thenReturn(contactInfo)
        whenever(groupPersistenceManager.getInfo(groupId)).thenReturn(groupInfo)

        newMessagesSubject.onNext(bundle)

        val notificationMessageInfo = NotificationMessageInfo(
            contactInfo.name,
            messageInfo.message,
            messageInfo.timestamp
        )

        body(contactInfo, groupInfo, notificationMessageInfo)
    }

    @Test
    fun `it should fetch group info when a groupId is specified in the MessageBundle`() {
        testGroupMessageBundle { contactInfo, groupInfo, messageInfo ->
            verify(groupPersistenceManager).getInfo(groupInfo.id)

        }
    }

    @Test
    fun `it should send ContactDisplayInfo with group info when a groupId is specified in the MessageBundle`() {
        testGroupMessageBundle { contactInfo, groupInfo, messageInfo ->
            val conversationInfo = NotificationConversationInfo.from(groupInfo)

            verify(platformNotificationsService).addNewMessageNotification(conversationInfo, messageInfo, 1)
        }
    }

    @Test
    fun `it should not fetch group info when no groupId is specified in the MessageBundle`() {
        val notifierService = initNotifierService()

        val userId = randomUserId()
        val contactInfo = randomContactInfo(AllowedMessageLevel.ALL)

        val bundle = MessageBundle(
            userId,
            null,
            listOf(randomReceivedMessageInfo())
        )

        whenever(contactsPersistenceManager.get(userId)).thenReturn(contactInfo)
        whenever(groupPersistenceManager.getInfo(any())).thenReturnNull()

        newMessagesSubject.onNext(bundle)

        verify(groupPersistenceManager, never()).getInfo(any())
    }

    @Test
    fun `it should clear only notifications for the focused group when a group is visited`() {
        val notifierService = initNotifierService()

        val contactInfo = randomContactInfo()
        val groupInfo = randomGroupInfo()

        whenever(contactsPersistenceManager.get(contactInfo.id)).thenReturn(contactInfo)
        whenever(groupPersistenceManager.getInfo(any())).thenReturn(groupInfo)


        val pageChangeEvent = PageChangeEvent(PageType.GROUP, groupInfo.id.string)
        uiEventSubject.onNext(pageChangeEvent)

        val conversationInfo = NotificationConversationInfo.from(groupInfo)
        verify(platformNotificationsService, times(1)).clearMessageNotificationsFor(conversationInfo)
    }
}

