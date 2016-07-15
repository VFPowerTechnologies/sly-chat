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
import org.junit.Ignore
import org.junit.Test
import java.util.*
import kotlin.test.assertEquals

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

    fun randomGroupId(): GroupId = GroupId(randomUUID())

    fun randomGroupName(): String = randomUUID()

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

    //XXX maybe we should do this in message receiver instead? although we want this to be handled in a transaction somehow...
    //or we could make every package message processing idempotent so it doesn't matter; inserting a dup message into a convo should just do nothing, etc
    //not aware of any message types that can't be idempotent
    //although this is not as "safe", it makes it a lot easier to handle package deletions in one spot and remove all the duplication from every message processing thing
    @Ignore
    @Test
    fun `it should remove the associated package after processing the message`() {}

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
        val groupId = randomGroupId()

        val m = GroupJoin(groupId, newMember)

        val service = createService()

        val groupInfo = GroupInfo(m.id, randomGroupName(), false, GroupMembershipLevel.PARTED)

        whenever(groupPersistenceManager.getGroupInfo(m.id)).thenReturn(groupInfo)

        service.processMessage(sender, wrap(m)).get()

        verify(groupPersistenceManager, never()).addMember(any(), any())
    }

    @Test
    fun `it should ignore group parts for parted groups`() {
        val sender = UserId(1)
        val groupId = randomGroupId()

        val m = GroupPart(groupId)

        val service = createService()

        val groupInfo = GroupInfo(m.id, randomGroupName(), false, GroupMembershipLevel.PARTED)

        whenever(groupPersistenceManager.getGroupInfo(m.id)).thenReturn(groupInfo)

        service.processMessage(sender, wrap(m)).get()

        verify(groupPersistenceManager, never()).removeMember(any(), any())
    }

    @Test
    fun `it should add a member when receiving a GroupJoin from a member for a joined group`() {
        val sender = UserId(1)
        val newMember = UserId(2)
        val groupId = randomGroupId()

        val m = GroupJoin(groupId, newMember)

        val service = createService()

        val groupInfo = GroupInfo(m.id, randomGroupName(), false, GroupMembershipLevel.JOINED)

        whenever(groupPersistenceManager.isUserMemberOf(sender, groupId)).thenReturn(true)

        whenever(groupPersistenceManager.getGroupInfo(m.id)).thenReturn(groupInfo)

        service.processMessage(sender, wrap(m)).get()

        verify(groupPersistenceManager).addMember(groupId, newMember)
    }

    @Test
    fun `it should remove a member when receiving a GroupPart from that user for a joined group`() {
        val sender = UserId(1)
        val groupId = randomGroupId()

        val m = GroupPart(groupId)

        val service = createService()

        val groupInfo = GroupInfo(m.id, randomGroupName(), false, GroupMembershipLevel.JOINED)

        whenever(groupPersistenceManager.isUserMemberOf(sender, groupId)).thenReturn(true)

        whenever(groupPersistenceManager.getGroupInfo(m.id)).thenReturn(groupInfo)

        service.processMessage(sender, wrap(m)).get()

        verify(groupPersistenceManager).removeMember(groupId, sender)
    }

    @Test
    fun `it should ignore an add from a non-member user for a joined group`() {
        val sender = UserId(1)
        val groupId = randomGroupId()
        val newMember = UserId(2)

        val m = GroupJoin(groupId, newMember)

        val service = createService()

        val groupInfo = GroupInfo(m.id, randomGroupName(), false, GroupMembershipLevel.JOINED)

        whenever(groupPersistenceManager.isUserMemberOf(sender, groupId)).thenReturn(false)

        whenever(groupPersistenceManager.getGroupInfo(m.id)).thenReturn(groupInfo)

        service.processMessage(sender, wrap(m)).get()

        verify(groupPersistenceManager, never()).addMember(any(), any())
    }

    @Test
    fun `it should ignore a part from a non-member user for a joined group`() {
        val sender = UserId(1)
        val groupId = randomGroupId()

        val m = GroupPart(groupId)

        val service = createService()

        val groupInfo = GroupInfo(m.id, randomGroupName(), false, GroupMembershipLevel.JOINED)

        whenever(groupPersistenceManager.isUserMemberOf(sender, groupId)).thenReturn(false)

        whenever(groupPersistenceManager.getGroupInfo(m.id)).thenReturn(groupInfo)

        service.processMessage(sender, wrap(m)).get()

        verify(groupPersistenceManager, never()).removeMember(any(), any())
    }

    @Test
    fun `it should ignore group joins for blocked groups`() {
        val sender = UserId(1)
        val groupId = randomGroupId()
        val newMember = UserId(2)

        val m = GroupJoin(groupId, newMember)

        val service = createService()

        val groupInfo = GroupInfo(m.id, randomGroupName(), false, GroupMembershipLevel.BLOCKED)

        whenever(groupPersistenceManager.getGroupInfo(m.id)).thenReturn(groupInfo)

        service.processMessage(sender, wrap(m)).get()

        verify(groupPersistenceManager, never()).addMember(any(), any())
    }

    @Test
    fun `it should ignore group parts for blocked groups`() {
        val sender = UserId(1)
        val groupId = randomGroupId()

        val m = GroupPart(groupId)

        val service = createService()

        val groupInfo = GroupInfo(m.id, randomGroupName(), false, GroupMembershipLevel.BLOCKED)

        whenever(groupPersistenceManager.getGroupInfo(m.id)).thenReturn(groupInfo)

        service.processMessage(sender, wrap(m)).get()

        verify(groupPersistenceManager, never()).removeMember(any(), any())
    }

    @Test
    fun `it should ignore group messages for parted groups`() {}

    @Test
    fun `it should ignore group messages for blocked groups`() {}

    @Test
    fun `it should store received group text messages to the proper group`() {}
}
