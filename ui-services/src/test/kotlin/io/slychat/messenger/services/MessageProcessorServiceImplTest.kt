package io.slychat.messenger.services

import com.nhaarman.mockito_kotlin.*
import io.slychat.messenger.core.UserId
import io.slychat.messenger.core.currentTimestamp
import io.slychat.messenger.core.persistence.*
import io.slychat.messenger.core.randomUUID
import io.slychat.messenger.testutils.KovenantTestModeRule
import io.slychat.messenger.testutils.testSubscriber
import io.slychat.messenger.testutils.thenReturn
import io.slychat.messenger.testutils.thenReturnNull
import nl.komponents.kovenant.Promise
import org.assertj.core.api.Assertions.assertThat
import org.junit.ClassRule
import org.junit.Test
import java.util.*
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class MessageProcessorServiceImplTest {
    companion object {
        @JvmField
        @ClassRule
        val kovenantTestMode = KovenantTestModeRule()
    }

    val contactsService: ContactsService = mock()
    val messagePersistenceManager: MessagePersistenceManager = mock()
    val groupPersistenceManager: GroupPersistenceManager = mock()

    fun createService(): MessageProcessorServiceImpl {
        whenever(messagePersistenceManager.addMessage(any(), any())).thenAnswer {
            @Suppress("UNCHECKED_CAST")
            val a = it.arguments[1] as MessageInfo
            Promise.ofSuccess<MessageInfo, Exception>(a)
        }

        whenever(groupPersistenceManager.addMessage(any(), any(), any())).thenAnswer {
            @Suppress("UNCHECKED_CAST")
            val a = it.arguments[2] as MessageInfo
            Promise.ofSuccess<MessageInfo, Exception>(a)
        }

        whenever(groupPersistenceManager.joinGroup(any(), any())).thenReturn(Unit)
        whenever(groupPersistenceManager.getGroupInfo(any())).thenReturnNull()
        whenever(groupPersistenceManager.addMember(any(), any())).thenReturn(true)
        whenever(groupPersistenceManager.removeMember(any(), any())).thenReturn(true)
        whenever(groupPersistenceManager.isUserMemberOf(any(), any())).thenReturn(true)

        return MessageProcessorServiceImpl(
            contactsService,
            messagePersistenceManager,
            groupPersistenceManager
        )
    }

    @Test
    fun `it should store newly received text messages`() {
        val service = createService()

        val m = TextMessage(currentTimestamp(), "m", null)

        val wrapper = SlyMessageWrapper(randomUUID(), TextMessageWrapper(m))

        service.processMessage(UserId(1), wrapper).get()

        verify(messagePersistenceManager).addMessage(eq(UserId(1)), any())
    }

    @Test
    fun `it should emit new message events after storing new text messages`() {
        val service = createService()

        val m = TextMessage(currentTimestamp(), "m", null)

        val wrapper = SlyMessageWrapper(randomUUID(), TextMessageWrapper(m))

        val testSubscriber = service.newMessages.testSubscriber()

        val from = UserId(1)

        service.processMessage(from, wrapper).get()

        val bundles = testSubscriber.onNextEvents

        assertThat(bundles)
            .hasSize(1)
            .`as`("Bundle check")

        val bundle = bundles[0]

        assertEquals(bundle.userId, from, "Invalid user id")
    }

    fun randomGroupInfo(isPending: Boolean, membershipLevel: GroupMembershipLevel): GroupInfo =
        GroupInfo(randomGroupId(), randomGroupName(), isPending, membershipLevel)

    fun randomGroupId(): GroupId = GroupId(randomUUID())

    fun randomGroupName(): String = randomUUID()

    fun randomTextMessage(groupId: GroupId? = null): TextMessage =
        TextMessage(currentTimestamp(), randomUUID(), groupId)

    fun returnGroupInfo(groupInfo: GroupInfo?) {
        if (groupInfo != null)
            whenever(groupPersistenceManager.getGroupInfo(groupInfo.id)).thenReturn(groupInfo)
        else
            whenever(groupPersistenceManager.getGroupInfo(any())).thenReturnNull()
    }

    fun wrap(m: TextMessage): SlyMessageWrapper = SlyMessageWrapper(randomUUID(), TextMessageWrapper(m))
    fun wrap(m: GroupEvent): SlyMessageWrapper = SlyMessageWrapper(randomUUID(), GroupEventWrapper(m))

    /* Group stuff */

    fun generateInvite(): GroupInvitation {
        val groupId = randomGroupId()
        val members = (1..3L).mapTo(HashSet()) { UserId(it) }

        return GroupInvitation(groupId, randomGroupName(), members)
    }

    @Test
    fun `it should add group info and fetch member contact info when receiving a new group invitation for an unknown group`() {
        val owner = UserId(1)

        val m = generateInvite()

        val service = createService()

        whenever(contactsService.addMissingContacts(any())).thenReturn(emptySet())

        whenever(groupPersistenceManager.getGroupInfo(m.id)).thenReturnNull()

        service.processMessage(owner, wrap(m)).get()

        val info = GroupInfo(m.id, m.name, true, GroupMembershipLevel.JOINED)

        verify(contactsService).addMissingContacts(m.members)

        verify(groupPersistenceManager).joinGroup(info, m.members)
    }

    @Test
    fun `it should ignore duplicate invitations`() {
        val owner = UserId(1)

        val m = generateInvite()

        val service = createService()

        val groupInfo = GroupInfo(m.id, m.name, true, GroupMembershipLevel.JOINED)

        whenever(groupPersistenceManager.getGroupInfo(m.id)).thenReturn(groupInfo)

        service.processMessage(owner, wrap(m)).get()

        verify(groupPersistenceManager, never()).joinGroup(any(), any())
    }

    @Test
    fun `it should filter out invalid user ids in group invitations`() {
        val owner = UserId(1)

        val m = generateInvite()

        val service = createService()

        val groupInfo = GroupInfo(m.id, m.name, true, GroupMembershipLevel.JOINED)

        val invalidUser = m.members.first()
        val remaining = HashSet(m.members)
        remaining.remove(invalidUser)

        whenever(groupPersistenceManager.getGroupInfo(m.id)).thenReturnNull()

        whenever(contactsService.addMissingContacts(anySet())).thenReturn(setOf(invalidUser))

        service.processMessage(owner, wrap(m)).get()

        verify(groupPersistenceManager).joinGroup(groupInfo, remaining)
    }

    //XXX should we just auto-add the sender to the membership list? seems like it's better than sending the member list including yourself all the time?
    @Test
    fun `it should not join groups if no members are present`() {}

    @Test
    fun `it should ignore invitations for parted groups which have been blocked`() {
        val owner = UserId(1)

        val m = generateInvite()

        val service = createService()

        val groupInfo = GroupInfo(m.id, m.name, false, GroupMembershipLevel.BLOCKED)

        whenever(groupPersistenceManager.getGroupInfo(m.id)).thenReturn(groupInfo)

        whenever(contactsService.addMissingContacts(anySet())).thenReturn(emptySet())

        service.processMessage(owner, wrap(m)).get()

        verify(groupPersistenceManager, never()).joinGroup(any(), any())
    }

    @Test
    fun `it should not ignore invitations for parted groups which have not been blocked`() {
        val owner = UserId(1)

        val m = generateInvite()

        val service = createService()

        val groupInfo = GroupInfo(m.id, m.name, false, GroupMembershipLevel.PARTED)

        whenever(groupPersistenceManager.getGroupInfo(m.id)).thenReturn(groupInfo)

        whenever(contactsService.addMissingContacts(anySet())).thenReturn(emptySet())

        service.processMessage(owner, wrap(m)).get()

        val newGroupInfo = GroupInfo(m.id, m.name, true, GroupMembershipLevel.JOINED)

        verify(groupPersistenceManager).joinGroup(newGroupInfo, m.members)

        verify(contactsService).addMissingContacts(m.members)
    }

    @Test
    fun `it should ignore group joins for parted groups`() {
        val sender = UserId(1)
        val newMember = UserId(2)
        val groupInfo = randomGroupInfo(false, GroupMembershipLevel.PARTED)

        val m = GroupJoin(groupInfo.id, newMember)

        val service = createService()

        whenever(groupPersistenceManager.getGroupInfo(m.id)).thenReturn(groupInfo)

        service.processMessage(sender, wrap(m)).get()

        verify(groupPersistenceManager, never()).addMember(any(), any())
    }

    @Test
    fun `it should ignore group parts for parted groups`() {
        val sender = UserId(1)
        val groupInfo = randomGroupInfo(false, GroupMembershipLevel.PARTED)

        val m = GroupPart(groupInfo.id)

        val service = createService()

        whenever(groupPersistenceManager.getGroupInfo(m.id)).thenReturn(groupInfo)

        service.processMessage(sender, wrap(m)).get()

        verify(groupPersistenceManager, never()).removeMember(any(), any())
    }

    @Test
    fun `it should add a member when receiving a GroupJoin from a member for a joined group`() {
        val sender = UserId(1)
        val newMember = UserId(2)
        val groupInfo = randomGroupInfo(false, GroupMembershipLevel.JOINED)

        val m = GroupJoin(groupInfo.id, newMember)

        val service = createService()

        whenever(groupPersistenceManager.isUserMemberOf(sender, groupInfo.id)).thenReturn(true)

        whenever(groupPersistenceManager.getGroupInfo(m.id)).thenReturn(groupInfo)

        service.processMessage(sender, wrap(m)).get()

        verify(groupPersistenceManager).addMember(groupInfo.id, newMember)
    }

    @Test
    fun `it should remove a member when receiving a GroupPart from that user for a joined group`() {
        val sender = UserId(1)
        val groupInfo = randomGroupInfo(false, GroupMembershipLevel.JOINED)

        val m = GroupPart(groupInfo.id)

        val service = createService()

        whenever(groupPersistenceManager.isUserMemberOf(sender, groupInfo.id)).thenReturn(true)

        whenever(groupPersistenceManager.getGroupInfo(m.id)).thenReturn(groupInfo)

        service.processMessage(sender, wrap(m)).get()

        verify(groupPersistenceManager).removeMember(groupInfo.id, sender)
    }

    @Test
    fun `it should ignore an add from a non-member user for a joined group`() {
        val sender = UserId(1)
        val newMember = UserId(2)

        val groupInfo = randomGroupInfo(false, GroupMembershipLevel.JOINED)

        val m = GroupJoin(groupInfo.id, newMember)

        val service = createService()

        whenever(groupPersistenceManager.isUserMemberOf(sender, groupInfo.id)).thenReturn(false)

        whenever(groupPersistenceManager.getGroupInfo(m.id)).thenReturn(groupInfo)

        service.processMessage(sender, wrap(m)).get()

        verify(groupPersistenceManager, never()).addMember(any(), any())
    }

    @Test
    fun `it should ignore a part from a non-member user for a joined group`() {
        val sender = UserId(1)
        val groupInfo = randomGroupInfo(false, GroupMembershipLevel.JOINED)

        val m = GroupPart(groupInfo.id)

        val service = createService()

        whenever(groupPersistenceManager.isUserMemberOf(sender, groupInfo.id)).thenReturn(false)

        whenever(groupPersistenceManager.getGroupInfo(m.id)).thenReturn(groupInfo)

        service.processMessage(sender, wrap(m)).get()

        verify(groupPersistenceManager, never()).removeMember(any(), any())
    }

    @Test
    fun `it should ignore group joins for blocked groups`() {
        val sender = UserId(1)
        val newMember = UserId(2)

        val groupId = randomGroupId()
        val groupInfo = randomGroupInfo(false, GroupMembershipLevel.BLOCKED)

        val m = GroupJoin(groupId, newMember)

        val service = createService()

        whenever(groupPersistenceManager.getGroupInfo(m.id)).thenReturn(groupInfo)

        service.processMessage(sender, wrap(m)).get()

        verify(groupPersistenceManager, never()).addMember(any(), any())
    }

    @Test
    fun `it should ignore group parts for blocked groups`() {
        val sender = UserId(1)
        val groupInfo = randomGroupInfo(false, GroupMembershipLevel.BLOCKED)

        val m = GroupPart(groupInfo.id)

        val service = createService()

        whenever(groupPersistenceManager.getGroupInfo(m.id)).thenReturn(groupInfo)

        service.processMessage(sender, wrap(m)).get()

        verify(groupPersistenceManager, never()).removeMember(any(), any())
    }

    @Test
    fun `it should store received group text messages to the proper group`() {
        val sender = UserId(1)

        val groupInfo = randomGroupInfo(false, GroupMembershipLevel.JOINED)
        val m = randomTextMessage(groupInfo.id)

        val service = createService()

        returnGroupInfo(groupInfo)

        service.processMessage(sender, wrap(m)).get()

        verify(groupPersistenceManager).addMessage(eq(groupInfo.id), eq(sender), capture { messageInfo ->
            assertFalse(messageInfo.isSent, "Message marked as sent")
            assertEquals(m.message, messageInfo.message, "Invalid message")
        })
    }

    @Test
    fun `it should emit a new message event when receiving a new group text message`() {
        val sender = UserId(1)

        val groupInfo = randomGroupInfo(false, GroupMembershipLevel.JOINED)
        val m = randomTextMessage(groupInfo.id)

        val service = createService()

        returnGroupInfo(groupInfo)

        val testSubscriber = service.newMessages.testSubscriber()

        val wrapper = wrap(m)
        service.processMessage(sender, wrapper).get()

        val newMessages = testSubscriber.onNextEvents
        assertEquals(1, newMessages.size, "Invalid number of new message events")

        val bundle = newMessages[0]
        assertEquals(1, bundle.messages.size, "Invalid number of messages in bundle")

        val message = bundle.messages[0]
        assertEquals(m.message, message.message, "Invalid message")
        assertEquals(wrapper.messageId, message.id, "Invalid message id")
    }

    fun testDropGroupTextMessage(senderIsMember: Boolean, membershipLevel: GroupMembershipLevel) {
        val sender = UserId(1)

        val groupInfo = randomGroupInfo(false, membershipLevel)
        val m = randomTextMessage(groupInfo.id)

        val service = createService()

        returnGroupInfo(groupInfo)

        whenever(groupPersistenceManager.isUserMemberOf(sender, groupInfo.id)).thenReturn(senderIsMember)

        service.processMessage(sender, wrap(m)).get()

        verify(groupPersistenceManager, never()).addMessage(any(), any(), any())
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
