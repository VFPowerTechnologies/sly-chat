package io.slychat.messenger.services

import com.nhaarman.mockito_kotlin.*
import io.slychat.messenger.core.*
import io.slychat.messenger.core.persistence.*
import io.slychat.messenger.services.config.UserConfig
import io.slychat.messenger.services.config.UserConfigService
import io.slychat.messenger.services.contacts.NotificationConversationInfo
import io.slychat.messenger.services.contacts.NotificationMessageInfo
import io.slychat.messenger.services.messaging.MessageBundle
import io.slychat.messenger.testutils.KovenantTestModeRule
import io.slychat.messenger.testutils.cond
import io.slychat.messenger.testutils.thenReturn
import io.slychat.messenger.testutils.thenReturnNull
import nl.komponents.kovenant.Promise
import org.assertj.core.api.Assertions.assertThat
import org.junit.Before
import org.junit.ClassRule
import org.junit.Test
import rx.Observable
import rx.subjects.BehaviorSubject
import rx.subjects.PublishSubject
import kotlin.test.assertFalse

@Suppress("UNUSED_VARIABLE")
class NotifierServiceImplTest {
    companion object {
        @JvmField
        @ClassRule
        val kovenantTestMode = KovenantTestModeRule()
    }

    val contactsPersistenceManager: ContactsPersistenceManager = mock()
    val groupPersistenceManager: GroupPersistenceManager = mock()
    val platformNotificationsService: PlatformNotificationService = mock()
    lateinit var userConfigService: UserConfigService

    val newMessagesSubject: PublishSubject<MessageBundle> = PublishSubject.create()
    val uiEventSubject: PublishSubject<UIEvent> = PublishSubject.create()
    val uiVisibility: BehaviorSubject<Boolean> = BehaviorSubject.create()

    @Before
    fun before() {
        whenever(groupPersistenceManager.getInfo(any())).thenReturnNull()
    }

    fun initNotifierService(isUiVisible: Boolean = false, config: UserConfig = UserConfig(notificationsEnabled = true)): NotifierServiceImpl {
        userConfigService = UserConfigService(mock(), config = config)

        uiVisibility.onNext(true)

        val notifierService = NotifierServiceImpl(
            newMessagesSubject,
            uiEventSubject,
            uiVisibility,
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

    fun testConvoNotificationDisplay(shouldShow: Boolean) {
        val contactInfo = setupContactInfo(1)

        val messages = (0..1).map {
            MessageInfo(randomUUID(), it.toString(), currentTimestamp(), currentTimestamp(), false, true, 0)
        }

        val messageBundle = MessageBundle(contactInfo.id, null, messages)

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
        val notifierService = initNotifierService(isUiVisible = false)

        testConvoNotificationDisplay(true)
    }

    @Test
    fun `it should not show notifications when notifications are disabled and the ui is not visible`() {
        val notifierService = initNotifierService(isUiVisible = false, config = UserConfig(notificationsEnabled = false))

        testConvoNotificationDisplay(false)
    }

    @Test
    fun `it should not show notifications for the currently open user page`() {
        val notifierService = initNotifierService(isUiVisible = true)

        setupContactInfo(1)

        uiEventSubject.onNext(PageChangeEvent(PageType.CONVO, "1"))

        testConvoNotificationDisplay(false)
    }

    @Test
    fun `it should show notifications for an unfocused user`() {
        val notifierService = initNotifierService(isUiVisible = true)

        setupContactInfo(2)

        uiEventSubject.onNext(PageChangeEvent(PageType.CONVO, "2"))

        testConvoNotificationDisplay(true)
    }

    @Test
    fun `it should not show notifications when the contact page page is focused`() {
        val notifierService = initNotifierService(isUiVisible = true)

        uiEventSubject.onNext(PageChangeEvent(PageType.CONTACTS, ""))

        testConvoNotificationDisplay(false)
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

    @Test
    fun `it should not display notifications directed at the currently focused group`() {
        val notifierService = initNotifierService(isUiVisible = true)

        val contactInfo = randomContactInfo()
        val groupInfo = randomGroupInfo()

        whenever(contactsPersistenceManager.get(contactInfo.id)).thenReturn(contactInfo)
        whenever(groupPersistenceManager.getInfo(any())).thenReturn(groupInfo)

        val pageChangeEvent = PageChangeEvent(PageType.GROUP, groupInfo.id.string)
        uiEventSubject.onNext(pageChangeEvent)

        val bundle = MessageBundle(
            contactInfo.id,
            groupInfo.id,
            listOf(randomReceivedMessageInfo())
        )

        newMessagesSubject.onNext(bundle)

        verify(platformNotificationsService, never()).addNewMessageNotification(any(), any(), any())
    }

    @Test
    fun `it should display group notifications for a sender when the sender single chat page is focused`() {
        val notifierService = initNotifierService(isUiVisible = true)

        val contactInfo = randomContactInfo()
        val groupInfo = randomGroupInfo()

        whenever(contactsPersistenceManager.get(contactInfo.id)).thenReturn(contactInfo)
        whenever(groupPersistenceManager.getInfo(any())).thenReturn(groupInfo)

        val pageChangeEvent = PageChangeEvent(PageType.CONVO, contactInfo.id.toString())
        uiEventSubject.onNext(pageChangeEvent)

        val bundle = MessageBundle(
            contactInfo.id,
            groupInfo.id,
            listOf(randomReceivedMessageInfo())
        )

        newMessagesSubject.onNext(bundle)

        verify(platformNotificationsService).addNewMessageNotification(any(), any(), any())
    }

    fun randomMessageBundle(userId: UserId? = null, nMessages: Int = 2, groupId: GroupId? = null): MessageBundle {
        val user = userId ?: randomUserId()
        return MessageBundle(user, groupId, randomReceivedMessageInfoList(nMessages))
    }

    fun getMessageIdsFromBundle(messageBundle: MessageBundle): List<String> = messageBundle.messages.map { it.id }

    fun flattenBundles(bundles: List<MessageBundle>): List<MessageBundle> {
        val flattened = NotifierServiceImpl.flattenMessageBundles(Observable.just(bundles))

        return flattened.toList().toBlocking().single()
    }

    @Test
    fun `flattenMessageBundles should group user MessageBundles together`() {
        val user1 = randomUserId()
        val user2 = randomUserId()

        val bundles = listOf(
            randomMessageBundle(user1, 1),
            randomMessageBundle(user1, 1),
            randomMessageBundle(user2, 1),
            randomMessageBundle(user2, 1)
        )

        val output = flattenBundles(bundles)

        assertThat(output).apply {
            `as`("Messages should be grouped")
            hasSize(2)
            have(cond("Message count") { it.messages.size == 2 })
        }

        val users = output.mapToSet { it.userId }
        assertThat(users).apply {
            `as`("Grouped users")
            containsOnly(user1, user2)
        }
    }

    @Test
    fun `flattenMessageBundles should preserve the message order`() {
        val userId = randomUserId()

        val bundles = listOf(
            randomMessageBundle(userId, 1),
            randomMessageBundle(userId, 1),
            randomMessageBundle(userId, 1),
            randomMessageBundle(userId, 1)
        )

        val order = bundles.map { it.messages.first().id }

        val output = flattenBundles(bundles)

        assertThat(output).apply {
            `as`("Messages should be grouped")
            hasSize(1)
        }

        val bundle = output.first()

        val got = bundle.messages.map { it.id }

        assertThat(got).apply {
            `as`("Messages should be in order")
            hasSize(4)
            containsExactlyElementsOf(order)
        }
    }

    @Test
    fun `flattenMessageBundles should group user and group bundles separately`() {
        val userId = randomUserId()
        val groupId = randomGroupId()

        val bundles = listOf(
            randomMessageBundle(userId, 1),
            randomMessageBundle(userId, 1, groupId)
        )

        val output = flattenBundles(bundles)

        assertThat(output).apply {
            `as`("Messages should be grouped by (userId, groupId)")
            hasSize(2)
        }

        val expected = setOf(
            userId to null,
            userId to groupId
        )

        val grouped = output.mapToSet { it.userId to it.groupId }

        assertThat(grouped).apply {
            `as`("Messages should be grouped by (userId, groupId)")
            hasSameElementsAs(expected)
        }
    }
}

