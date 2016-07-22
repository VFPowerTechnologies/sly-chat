package io.slychat.messenger.services.messaging

import com.nhaarman.mockito_kotlin.*
import io.slychat.messenger.core.*
import io.slychat.messenger.core.persistence.*
import io.slychat.messenger.services.assertEventEmitted
import io.slychat.messenger.services.assertNoEventsEmitted
import io.slychat.messenger.services.contacts.ContactsService
import io.slychat.messenger.services.subclassFilterTestSubscriber
import io.slychat.messenger.testutils.*
import nl.komponents.kovenant.Promise
import org.assertj.core.api.Assertions.assertThat
import org.junit.ClassRule
import org.junit.Test
import rx.observers.TestSubscriber
import java.util.*
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class MessageProcessorImplTest {
    companion object {
        @JvmField
        @ClassRule
        val kovenantTestMode = KovenantTestModeRule()
    }

    val contactsService: ContactsService = mock()
    val messagePersistenceManager: MessagePersistenceManager = mock()
    val groupPersistenceManager: GroupPersistenceManager = mock()

    fun createProcessor(): MessageProcessorImpl {
        whenever(messagePersistenceManager.addMessage(any(), any())).thenAnswer {
            @Suppress("UNCHECKED_CAST")
            val a = it.arguments[1] as MessageInfo
            Promise.ofSuccess<MessageInfo, Exception>(a)
        }

        whenever(contactsService.addMissingContacts(any())).thenReturn(emptySet())

        whenever(groupPersistenceManager.addMessage(any(), any())).thenAnswer {
            @Suppress("UNCHECKED_CAST")
            val a = it.arguments[1] as GroupMessageInfo
            Promise.ofSuccess<GroupMessageInfo, Exception>(a)
        }

        whenever(groupPersistenceManager.join(any(), any())).thenReturn(Unit)
        whenever(groupPersistenceManager.getInfo(any())).thenReturnNull()
        whenever(groupPersistenceManager.addMembers(any(), any())).thenAnswerWithArg(1)
        whenever(groupPersistenceManager.removeMember(any(), any())).thenReturn(true)
        whenever(groupPersistenceManager.isUserMemberOf(any(), any())).thenReturn(true)

        return MessageProcessorImpl(
            contactsService,
            messagePersistenceManager,
            groupPersistenceManager
        )
    }

    inline fun <reified T : GroupEvent> groupEventCollectorFor(messageProcessorService: MessageProcessorImpl): TestSubscriber<T> {
        return messageProcessorService.groupEvents.subclassFilterTestSubscriber()
    }

    @Test
    fun `it should store newly received text messages`() {
        val processor = createProcessor()

        val m = TextMessage(currentTimestamp(), "m", null)

        val wrapper = SlyMessageWrapper(randomUUID(), TextMessageWrapper(m))

        processor.processMessage(UserId(1), wrapper).get()

        verify(messagePersistenceManager).addMessage(eq(UserId(1)), any())
    }

    @Test
    fun `it should emit new message events after storing new text messages`() {
        val processor = createProcessor()

        val m = TextMessage(currentTimestamp(), "m", null)

        val wrapper = SlyMessageWrapper(randomUUID(), TextMessageWrapper(m))

        val testSubscriber = processor.newMessages.testSubscriber()

        val from = UserId(1)

        processor.processMessage(from, wrapper).get()

        val bundles = testSubscriber.onNextEvents

        assertThat(bundles)
            .hasSize(1)
            .`as`("Bundle check")

        val bundle = bundles[0]

        assertEquals(bundle.userId, from, "Invalid user id")
    }

    fun randomTextMessage(groupId: GroupId? = null): TextMessage =
        TextMessage(currentTimestamp(), randomUUID(), groupId)

    fun returnGroupInfo(groupInfo: GroupInfo?) {
        if (groupInfo != null)
            whenever(groupPersistenceManager.getInfo(groupInfo.id)).thenReturn(groupInfo)
        else
            whenever(groupPersistenceManager.getInfo(any())).thenReturnNull()
    }

    fun wrap(m: TextMessage): SlyMessageWrapper = SlyMessageWrapper(randomUUID(), TextMessageWrapper(m))
    fun wrap(m: GroupEventMessage): SlyMessageWrapper = SlyMessageWrapper(randomUUID(), GroupEventMessageWrapper(m))

    /* Group stuff */

    fun generateInvite(): GroupEventMessage.Invitation {
        val groupId = randomGroupId()
        val members = (1..3L).mapTo(HashSet()) { UserId(it) }

        return GroupEventMessage.Invitation(groupId, randomGroupName(), members)
    }

    @Test
    fun `it should add group info and fetch member contact info when receiving a new group invitation for an unknown group`() {
        val sender = randomUserId()

        val m = generateInvite()

        val fullMembers = HashSet(m.members)
        fullMembers.add(sender)

        val processor = createProcessor()

        processor.processMessage(sender, wrap(m)).get()

        val info = GroupInfo(m.id, m.name, true, GroupMembershipLevel.JOINED)

        verify(contactsService).addMissingContacts(m.members)

        verify(groupPersistenceManager).join(info, fullMembers)
    }

    @Test
    fun `it should emit a NewGroup event upon processing a new invitation (all valid users)`() {
        val sender = randomUserId()

        val m = generateInvite()

        val fullMembers = HashSet(m.members)
        fullMembers.add(sender)

        val processor = createProcessor()

        val testSubscriber = groupEventCollectorFor<GroupEvent.NewGroup>(processor)

        processor.processMessage(sender, wrap(m)).get()

        assertEventEmitted(testSubscriber) { ev ->
            assertEquals(m.id, ev.id, "Invalid id")
            assertEquals(fullMembers, ev.members, "Invalid member list")
        }
    }

    @Test
    fun `it should emit a NewGroup event upon processing a new invitation (some invalid users)`() {
        val sender = randomUserId()

        val m = generateInvite()

        val invalidUser = m.members.first()
        val remaining = HashSet(m.members)
        remaining.add(sender)
        remaining.remove(invalidUser)

        val processor = createProcessor()

        val testSubscriber = groupEventCollectorFor<GroupEvent.NewGroup>(processor)

        whenever(contactsService.addMissingContacts(anySet())).thenReturn(setOf(invalidUser))

        processor.processMessage(sender, wrap(m)).get()

        assertEventEmitted(testSubscriber) { ev ->
            assertEquals(m.id, ev.id, "Invalid id")
            assertEquals(remaining, ev.members, "Invalid member list")
        }
    }

    @Test
    fun `it should ignore duplicate invitations`() {
        val sender = randomUserId()

        val m = generateInvite()

        val processor = createProcessor()

        val groupInfo = GroupInfo(m.id, m.name, true, GroupMembershipLevel.JOINED)

        whenever(groupPersistenceManager.getInfo(m.id)).thenReturn(groupInfo)

        processor.processMessage(sender, wrap(m)).get()

        verify(groupPersistenceManager, never()).join(any(), any())
    }

    @Test
    fun `it should filter out invalid user ids in group invitations`() {
        val sender = randomUserId()

        val m = generateInvite()

        val process = createProcessor()

        val groupInfo = GroupInfo(m.id, m.name, true, GroupMembershipLevel.JOINED)

        val invalidUser = m.members.first()
        val remaining = HashSet(m.members)
        remaining.add(sender)
        remaining.remove(invalidUser)

        whenever(groupPersistenceManager.getInfo(m.id)).thenReturnNull()

        whenever(contactsService.addMissingContacts(anySet())).thenReturn(setOf(invalidUser))

        process.processMessage(sender, wrap(m)).get()

        verify(groupPersistenceManager).join(groupInfo, remaining)
    }

    @Test
    fun `it should handle an empty members list invitation`() {
        val sender = randomUserId()

        val m = GroupEventMessage.Invitation(randomGroupId(), randomGroupName(), emptySet())

        val process = createProcessor()

        val groupInfo = GroupInfo(m.id, m.name, true, GroupMembershipLevel.JOINED)

        whenever(groupPersistenceManager.getInfo(m.id)).thenReturnNull()

        process.processMessage(sender, wrap(m)).get()

        verify(contactsService).addMissingContacts(emptySet())

        verify(groupPersistenceManager).join(groupInfo, setOf(sender))
    }

    @Test
    fun `it should ignore invitations for parted groups which have been blocked`() {
        val sender = randomUserId()

        val m = generateInvite()

        val processor = createProcessor()

        val groupInfo = GroupInfo(m.id, m.name, false, GroupMembershipLevel.BLOCKED)

        whenever(groupPersistenceManager.getInfo(m.id)).thenReturn(groupInfo)

        whenever(contactsService.addMissingContacts(anySet())).thenReturn(emptySet())

        processor.processMessage(sender, wrap(m)).get()

        verify(groupPersistenceManager, never()).join(any(), any())
    }

    @Test
    fun `it should not ignore invitations for parted groups which have not been blocked`() {
        val sender = randomUserId()

        val m = generateInvite()

        val fullMembers = HashSet(m.members)
        fullMembers.add(sender)

        val processor = createProcessor()

        val groupInfo = GroupInfo(m.id, m.name, false, GroupMembershipLevel.PARTED)

        whenever(groupPersistenceManager.getInfo(m.id)).thenReturn(groupInfo)

        processor.processMessage(sender, wrap(m)).get()

        val newGroupInfo = GroupInfo(m.id, m.name, true, GroupMembershipLevel.JOINED)

        verify(groupPersistenceManager).join(newGroupInfo, fullMembers)

        verify(contactsService).addMissingContacts(m.members)
    }

    @Test
    fun `it should ignore group joins for parted groups`() {
        val sender = UserId(1)
        val newMember = UserId(2)
        val groupInfo = randomGroupInfo(GroupMembershipLevel.PARTED)

        val m = GroupEventMessage.Join(groupInfo.id, newMember)

        val processor = createProcessor()

        whenever(groupPersistenceManager.getInfo(m.id)).thenReturn(groupInfo)

        processor.processMessage(sender, wrap(m)).get()

        verify(groupPersistenceManager, never()).addMembers(any(), any())
    }

    @Test
    fun `it should ignore group parts for parted groups`() {
        val sender = randomUserId()
        val groupInfo = randomGroupInfo(GroupMembershipLevel.PARTED)

        val m = GroupEventMessage.Part(groupInfo.id)

        val processor = createProcessor()

        whenever(groupPersistenceManager.getInfo(m.id)).thenReturn(groupInfo)

        processor.processMessage(sender, wrap(m)).get()

        verify(groupPersistenceManager, never()).removeMember(any(), any())
    }

    @Test
    fun `it should add a member when receiving a new member join from a member for a joined group`() {
        val sender = UserId(1)
        val newMember = UserId(2)
        val groupInfo = randomGroupInfo(GroupMembershipLevel.JOINED)

        val m = GroupEventMessage.Join(groupInfo.id, newMember)

        val processor = createProcessor()

        whenever(groupPersistenceManager.isUserMemberOf(groupInfo.id, sender)).thenReturn(true)

        whenever(groupPersistenceManager.getInfo(m.id)).thenReturn(groupInfo)

        processor.processMessage(sender, wrap(m)).get()

        verify(groupPersistenceManager).addMembers(groupInfo.id, setOf(newMember))
    }

    @Test
    fun `it should emit a Join event when receiving a group join`() {
        val sender = UserId(1)
        val newMember = UserId(2)
        val groupInfo = randomGroupInfo(GroupMembershipLevel.JOINED)

        val m = GroupEventMessage.Join(groupInfo.id, newMember)

        val processor = createProcessor()

        whenever(groupPersistenceManager.getInfo(m.id)).thenReturn(groupInfo)

        processor.processMessage(sender, wrap(m)).get()
    }

    @Test
    fun `it should remove a member when receiving a group part from that user for a joined group`() {
        val sender = UserId(1)
        val groupInfo = randomGroupInfo(GroupMembershipLevel.JOINED)

        val m = GroupEventMessage.Part(groupInfo.id)

        val processor = createProcessor()

        whenever(groupPersistenceManager.isUserMemberOf(groupInfo.id, sender)).thenReturn(true)

        whenever(groupPersistenceManager.getInfo(m.id)).thenReturn(groupInfo)

        processor.processMessage(sender, wrap(m)).get()

        verify(groupPersistenceManager).removeMember(groupInfo.id, sender)
    }

    @Test
    fun `it should ignore an add from a non-member user for a joined group`() {
        val sender = UserId(1)
        val newMember = UserId(2)

        val groupInfo = randomGroupInfo(GroupMembershipLevel.JOINED)

        val m = GroupEventMessage.Join(groupInfo.id, newMember)

        val processor = createProcessor()

        whenever(groupPersistenceManager.isUserMemberOf(groupInfo.id, sender)).thenReturn(false)

        whenever(groupPersistenceManager.getInfo(m.id)).thenReturn(groupInfo)

        processor.processMessage(sender, wrap(m)).get()

        verify(groupPersistenceManager, never()).addMembers(any(), any())
    }

    @Test
    fun `it should ignore a part from a non-member user for a joined group`() {
        val sender = randomUserId()
        val groupInfo = randomGroupInfo(GroupMembershipLevel.JOINED)

        val m = GroupEventMessage.Part(groupInfo.id)

        val processor = createProcessor()

        whenever(groupPersistenceManager.isUserMemberOf(groupInfo.id, sender)).thenReturn(false)

        whenever(groupPersistenceManager.getInfo(m.id)).thenReturn(groupInfo)

        processor.processMessage(sender, wrap(m)).get()

        verify(groupPersistenceManager, never()).removeMember(any(), any())
    }

    @Test
    fun `it should ignore group joins for blocked groups`() {
        val sender = UserId(1)
        val newMember = UserId(2)

        val groupId = randomGroupId()
        val groupInfo = randomGroupInfo(GroupMembershipLevel.BLOCKED)

        val m = GroupEventMessage.Join(groupId, newMember)

        val processor = createProcessor()

        whenever(groupPersistenceManager.getInfo(m.id)).thenReturn(groupInfo)

        processor.processMessage(sender, wrap(m)).get()

        verify(groupPersistenceManager, never()).addMembers(any(), any())
    }

    @Test
    fun `it should ignore group parts for blocked groups`() {
        val sender = randomUserId()
        val groupInfo = randomGroupInfo(GroupMembershipLevel.BLOCKED)

        val m = GroupEventMessage.Part(groupInfo.id)

        val processor = createProcessor()

        whenever(groupPersistenceManager.getInfo(m.id)).thenReturn(groupInfo)

        processor.processMessage(sender, wrap(m)).get()

        verify(groupPersistenceManager, never()).removeMember(any(), any())
    }

    @Test
    fun `it should fetch remote contact info when receiving a group join`() {
        val sender = UserId(1)
        val newMember = UserId(2)
        val groupInfo = randomGroupInfo(GroupMembershipLevel.JOINED)

        val m = GroupEventMessage.Join(groupInfo.id, newMember)

        val processor = createProcessor()

        returnGroupInfo(groupInfo)

        whenever(groupPersistenceManager.addMembers(groupInfo.id, setOf(newMember))).thenAnswerWithArg(1)
        whenever(contactsService.addMissingContacts(any())).thenReturn(emptySet())

        processor.processMessage(sender, wrap(m)).get()

        verify(contactsService).addMissingContacts(setOf(newMember))
    }

    @Test
    fun `it should not add a member if the user id is invalid when receiving a group join`() {
        val sender = UserId(1)
        val newMember = UserId(2)
        val groupInfo = randomGroupInfo(GroupMembershipLevel.JOINED)

        val m = GroupEventMessage.Join(groupInfo.id, newMember)

        val processor = createProcessor()

        returnGroupInfo(groupInfo)

        whenever(contactsService.addMissingContacts(any())).thenReturn(setOf(newMember))

        processor.processMessage(sender, wrap(m)).get()

        verify(groupPersistenceManager, never()).addMembers(any(), any())
    }

    fun testJoinEvent(shouldEventBeEmitted: Boolean) {
        val sender = UserId(1)
        val newMember = UserId(2)
        val groupInfo = randomGroupInfo(GroupMembershipLevel.JOINED)

        val m = GroupEventMessage.Join(groupInfo.id, newMember)

        val processor = createProcessor()

        returnGroupInfo(groupInfo)

        val testSubscriber = groupEventCollectorFor<GroupEvent.Joined>(processor)

        val ongoing = whenever(groupPersistenceManager.addMembers(groupInfo.id, setOf(newMember)))
        if (shouldEventBeEmitted)
            ongoing.thenAnswerWithArg(1)
        else
            ongoing.thenReturn(emptySet())

        processor.processMessage(sender, wrap(m)).get()

        if (shouldEventBeEmitted) {
            assertEventEmitted(testSubscriber) { event ->
                assertEquals(groupInfo.id, event.id, "Invalid group id")
                assertEquals(setOf(newMember), event.users, "Invalid new member id")
            }
        }
        else
            assertNoEventsEmitted(testSubscriber)
    }

    @Test
    fun `it should emit a new join event after adding a new member for an existing group`() {
        testJoinEvent(true)
    }

    @Test
    fun `it not should emit a new join event if a member already exists in a group`() {
        testJoinEvent(false)
    }

    fun testPartEvent(shouldEventBeEmitted: Boolean) {
        val sender = randomUserId()
        val groupInfo = randomGroupInfo(GroupMembershipLevel.JOINED)

        val m = GroupEventMessage.Part(groupInfo.id)

        val processor = createProcessor()

        returnGroupInfo(groupInfo)

        whenever(groupPersistenceManager.removeMember(groupInfo.id, sender)).thenReturn(shouldEventBeEmitted)

        val testSubscriber = groupEventCollectorFor<GroupEvent.Parted>(processor)

        processor.processMessage(sender, wrap(m)).get()

        if (shouldEventBeEmitted) {
            assertEventEmitted(testSubscriber) { event ->
                assertEquals(groupInfo.id, event.id, "Invalid group id")
                assertEquals(sender, event.userId, "Invalid new member id")
            }
        }
        else
            assertNoEventsEmitted(testSubscriber)
    }

    @Test
    fun `it should emit a new part event after removing a new member for an existing group`() {
        testPartEvent(true)
    }

    @Test
    fun `it should not emit a new part event when receiving a part if a member does not exist in a group`() {
        testPartEvent(false)
    }

    @Test
    fun `it should store received group text messages to the proper group`() {
        val sender = randomUserId()

        val groupInfo = randomGroupInfo(GroupMembershipLevel.JOINED)
        val m = randomTextMessage(groupInfo.id)

        val processor = createProcessor()

        returnGroupInfo(groupInfo)

        processor.processMessage(sender, wrap(m)).get()

        verify(groupPersistenceManager).addMessage(eq(groupInfo.id), capture { groupMessageInfo ->
            val messageInfo = groupMessageInfo.info
            assertFalse(messageInfo.isSent, "Message marked as sent")
            assertEquals(m.message, messageInfo.message, "Invalid message")
        })
    }

    @Test
    fun `it should emit a new message event when receiving a new group text message`() {
        val sender = randomUserId()

        val groupInfo = randomGroupInfo(GroupMembershipLevel.JOINED)
        val m = randomTextMessage(groupInfo.id)

        val processor = createProcessor()

        returnGroupInfo(groupInfo)

        val testSubscriber = processor.newMessages.testSubscriber()

        val wrapper = wrap(m)
        processor.processMessage(sender, wrapper).get()

        val newMessages = testSubscriber.onNextEvents
        assertEquals(1, newMessages.size, "Invalid number of new message events")

        val bundle = newMessages[0]
        assertEquals(1, bundle.messages.size, "Invalid number of messages in bundle")

        val message = bundle.messages[0]
        assertEquals(m.message, message.message, "Invalid message")
        assertEquals(wrapper.messageId, message.id, "Invalid message id")
    }

    fun testDropGroupTextMessage(senderIsMember: Boolean, membershipLevel: GroupMembershipLevel) {
        val sender = randomUserId()

        val groupInfo = randomGroupInfo(membershipLevel)
        val m = randomTextMessage(groupInfo.id)

        val processor = createProcessor()

        returnGroupInfo(groupInfo)

        whenever(groupPersistenceManager.isUserMemberOf(groupInfo.id, sender)).thenReturn(senderIsMember)

        processor.processMessage(sender, wrap(m)).get()

        verify(groupPersistenceManager, never()).addMessage(any(), any())
    }

    @Test
    fun `it should ignore group text messages from non-members for joined groups`() {
        testDropGroupTextMessage(false, GroupMembershipLevel.JOINED)
    }

    @Test
    fun `it should ignore group text messages for parted groups`() {
        testDropGroupTextMessage(true, GroupMembershipLevel.PARTED)
    }

    @Test
    fun `it should ignore group text messages for blocked groups`() {
        testDropGroupTextMessage(true, GroupMembershipLevel.BLOCKED)
    }
}
