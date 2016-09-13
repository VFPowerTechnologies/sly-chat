package io.slychat.messenger.services.messaging

import com.nhaarman.mockito_kotlin.*
import io.slychat.messenger.core.*
import io.slychat.messenger.core.crypto.randomMessageId
import io.slychat.messenger.core.crypto.randomUUID
import io.slychat.messenger.core.persistence.*
import io.slychat.messenger.core.persistence.sqlite.InvalidMessageLevelException
import io.slychat.messenger.services.GroupService
import io.slychat.messenger.services.PageType
import io.slychat.messenger.services.SyncMessageFromOtherSecurityException
import io.slychat.messenger.services.UIEvent
import io.slychat.messenger.services.contacts.ContactsService
import io.slychat.messenger.services.crypto.MessageCipherService
import io.slychat.messenger.testutils.KovenantTestModeRule
import io.slychat.messenger.testutils.thenReject
import io.slychat.messenger.testutils.thenResolve
import org.junit.ClassRule
import org.junit.Test
import rx.subjects.PublishSubject
import java.util.*
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class MessageProcessorImplTest {
    companion object {
        @JvmField
        @ClassRule
        val kovenantTestMode = KovenantTestModeRule()
    }

    val selfId = randomUserId()

    val contactsService: ContactsService = mock()
    val messageService: MessageService = mock()
    val messageCipherService: MessageCipherService = mock()
    val groupService: GroupService = mock()
    val uiEvents: PublishSubject<UIEvent> = PublishSubject.create()

    fun randomTextMessage(groupId: GroupId? = null): TextMessage =
        TextMessage(randomMessageId(), currentTimestamp(), randomUUID(), groupId, randomInt(50, 100).toLong())

    fun returnGroupInfo(groupInfo: GroupInfo?) {
        if (groupInfo != null)
            whenever(groupService.getInfo(groupInfo.id)).thenResolve(groupInfo)
        else
            whenever(groupService.getInfo(any())).thenResolve(null)
    }

    fun wrap(m: TextMessage): SlyMessage = SlyMessage.Text(m)
    fun wrap(m: GroupEventMessage): SlyMessage = SlyMessage.GroupEvent(m)
    fun wrap(m: SyncMessage): SlyMessage = SlyMessage.Sync(m)
    fun wrap(m: ControlMessage): SlyMessage = SlyMessage.Control(m)

    fun createProcessor(): MessageProcessorImpl {
        whenever(messageService.addMessage(any(), any())).thenResolve(Unit)

        whenever(contactsService.addMissingContacts(any())).thenResolve(emptySet())
        whenever(contactsService.addById(any())).thenResolve(true)

        whenever(groupService.join(any(), any())).thenResolve(Unit)
        whenever(groupService.getInfo(any())).thenResolve(null)
        whenever(groupService.addMembers(any(), any())).thenResolve(Unit)
        whenever(groupService.removeMember(any(), any())).thenResolve(Unit)
        whenever(groupService.isUserMemberOf(any(), any())).thenResolve(true)

        whenever(messageCipherService.addSelfDevice(any())).thenResolve(Unit)

        return MessageProcessorImpl(
            selfId,
            contactsService,
            messageService,
            messageCipherService,
            groupService,
            uiEvents
        )
    }

    @Test
    fun `it should store newly received text messages`() {
        val processor = createProcessor()

        val m = randomTextMessage()

        val message = SlyMessage.Text(m)

        val sender = UserId(1)

        processor.processMessage(sender, message).get()

        verify(messageService).addMessage(eq(sender.toConversationId()), capture {
            val info = it.info
            assertEquals(m.ttl, info.ttl, "Invalid TTL")
            assertFalse(info.isRead, "Message should not be marked as read")
            assertEquals(m.message, info.message, "Invalid message text")
        })
    }

    @Test
    fun `it should mark new messages for the currently single convo focused page as read`() {
        val processor = createProcessor()

        val m = randomTextMessage()

        val message = SlyMessage.Text(m)

        val sender = UserId(1)

        uiEvents.onNext(UIEvent.PageChange(PageType.CONVO, sender.toString()))

        processor.processMessage(sender, message).get()

        verify(messageService).addMessage(eq(sender.toConversationId()), capture {
            assertTrue(it.info.isRead, "Message should be marked as read")
        })
    }

    @Test
    fun `it should handle InvalidMessageLevelException by calling ContactsService and retrying afterwards`() {
        val processor = createProcessor()

        val m = randomTextMessage()

        val message = SlyMessage.Text(m)

        val from = randomUserId()

        whenever(contactsService.allowAll(from)).thenResolve(Unit)
        whenever(messageService.addMessage(any(), any()))
            .thenReject(InvalidMessageLevelException(from))
            .thenResolve(Unit)

        processor.processMessage(from, message).get()

        verify(contactsService).allowAll(from)
        verify(messageService, times(2)).addMessage(any(), any())
    }

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

        val info = GroupInfo(m.id, m.name, GroupMembershipLevel.JOINED)

        verify(contactsService).addMissingContacts(m.members)

        verify(groupService).join(info, fullMembers)
    }

    @Test
    fun `it should not add itself as a member when receiving an invitation from another device`() {
        val m = generateInvite()

        val processor = createProcessor()

        processor.processMessage(selfId, wrap(m)).get()

        val info = GroupInfo(m.id, m.name, GroupMembershipLevel.JOINED)

        verify(contactsService).addMissingContacts(m.members)

        verify(groupService).join(info, m.members)
    }

    @Test
    fun `it should ignore duplicate invitations`() {
        val sender = randomUserId()

        val m = generateInvite()

        val processor = createProcessor()

        val groupInfo = GroupInfo(m.id, m.name, GroupMembershipLevel.JOINED)

        whenever(groupService.getInfo(m.id)).thenResolve(groupInfo)

        processor.processMessage(sender, wrap(m)).get()

        verify(groupService, never()).join(any(), any())
    }

    @Test
    fun `it should filter out invalid user ids in group invitations`() {
        val sender = randomUserId()

        val m = generateInvite()

        val process = createProcessor()

        val groupInfo = GroupInfo(m.id, m.name, GroupMembershipLevel.JOINED)

        val invalidUser = m.members.first()
        val remaining = HashSet(m.members)
        remaining.add(sender)
        remaining.remove(invalidUser)

        whenever(groupService.getInfo(m.id)).thenResolve(null)

        whenever(contactsService.addMissingContacts(anySet())).thenResolve(setOf(invalidUser))

        process.processMessage(sender, wrap(m)).get()

        verify(groupService).join(groupInfo, remaining)
    }

    @Test
    fun `it should handle an empty members list invitation`() {
        val sender = randomUserId()

        val m = GroupEventMessage.Invitation(randomGroupId(), randomGroupName(), emptySet())

        val process = createProcessor()

        val groupInfo = GroupInfo(m.id, m.name, GroupMembershipLevel.JOINED)

        whenever(groupService.getInfo(m.id)).thenResolve(null)

        process.processMessage(sender, wrap(m)).get()

        verify(contactsService).addMissingContacts(emptySet())

        verify(groupService).join(groupInfo, setOf(sender))
    }

    @Test
    fun `it should ignore invitations for parted groups which have been blocked`() {
        val sender = randomUserId()

        val m = generateInvite()

        val processor = createProcessor()

        val groupInfo = GroupInfo(m.id, m.name, GroupMembershipLevel.BLOCKED)

        whenever(groupService.getInfo(m.id)).thenResolve(groupInfo)

        whenever(contactsService.addMissingContacts(anySet())).thenResolve(emptySet())

        processor.processMessage(sender, wrap(m)).get()

        verify(groupService, never()).join(any(), any())
    }

    @Test
    fun `it should not ignore invitations for parted groups which have not been blocked`() {
        val sender = randomUserId()

        val m = generateInvite()

        val fullMembers = HashSet(m.members)
        fullMembers.add(sender)

        val processor = createProcessor()

        val groupInfo = GroupInfo(m.id, m.name, GroupMembershipLevel.PARTED)

        whenever(groupService.getInfo(m.id)).thenResolve(groupInfo)

        processor.processMessage(sender, wrap(m)).get()

        val newGroupInfo = GroupInfo(m.id, m.name, GroupMembershipLevel.JOINED)

        verify(groupService).join(newGroupInfo, fullMembers)

        verify(contactsService).addMissingContacts(m.members)
    }

    @Test
    fun `it should ignore group joins for parted groups`() {
        val sender = UserId(1)
        val newMember = UserId(2)
        val groupInfo = randomGroupInfo(GroupMembershipLevel.PARTED)

        val m = GroupEventMessage.Join(groupInfo.id, newMember)

        val processor = createProcessor()

        whenever(groupService.getInfo(m.id)).thenResolve(groupInfo)

        processor.processMessage(sender, wrap(m)).get()

        verify(groupService, never()).addMembers(any(), any())
    }

    @Test
    fun `it should ignore group parts for parted groups`() {
        val sender = randomUserId()
        val groupInfo = randomGroupInfo(GroupMembershipLevel.PARTED)

        val m = GroupEventMessage.Part(groupInfo.id)

        val processor = createProcessor()

        whenever(groupService.getInfo(m.id)).thenResolve(groupInfo)

        processor.processMessage(sender, wrap(m)).get()

        verify(groupService, never()).removeMember(any(), any())
    }

    @Test
    fun `it should add a member when receiving a new member join from a member for a joined group`() {
        val sender = UserId(1)
        val newMember = UserId(2)
        val groupInfo = randomGroupInfo(GroupMembershipLevel.JOINED)

        val m = GroupEventMessage.Join(groupInfo.id, newMember)

        val processor = createProcessor()

        whenever(groupService.isUserMemberOf(groupInfo.id, sender)).thenResolve(true)

        whenever(groupService.getInfo(m.id)).thenResolve(groupInfo)

        processor.processMessage(sender, wrap(m)).get()

        verify(groupService).addMembers(groupInfo.id, setOf(newMember))
    }

    @Test
    fun `it should emit a Join event when receiving a group join`() {
        val sender = UserId(1)
        val newMember = UserId(2)
        val groupInfo = randomGroupInfo(GroupMembershipLevel.JOINED)

        val m = GroupEventMessage.Join(groupInfo.id, newMember)

        val processor = createProcessor()

        whenever(groupService.getInfo(m.id)).thenResolve(groupInfo)

        processor.processMessage(sender, wrap(m)).get()
    }

    @Test
    fun `it should remove a member when receiving a group part from that user for a joined group`() {
        val sender = UserId(1)
        val groupInfo = randomGroupInfo(GroupMembershipLevel.JOINED)

        val m = GroupEventMessage.Part(groupInfo.id)

        val processor = createProcessor()

        whenever(groupService.isUserMemberOf(groupInfo.id, sender)).thenResolve(true)

        whenever(groupService.getInfo(m.id)).thenResolve(groupInfo)

        processor.processMessage(sender, wrap(m)).get()

        verify(groupService).removeMember(groupInfo.id, sender)
    }

    @Test
    fun `it should ignore an add from a non-member user for a joined group`() {
        val sender = UserId(1)
        val newMember = UserId(2)

        val groupInfo = randomGroupInfo(GroupMembershipLevel.JOINED)

        val m = GroupEventMessage.Join(groupInfo.id, newMember)

        val processor = createProcessor()

        whenever(groupService.isUserMemberOf(groupInfo.id, sender)).thenResolve(false)

        whenever(groupService.getInfo(m.id)).thenResolve(groupInfo)

        processor.processMessage(sender, wrap(m)).get()

        verify(groupService, never()).addMembers(any(), any())
    }

    @Test
    fun `it should ignore a part from a non-member user for a joined group`() {
        val sender = randomUserId()
        val groupInfo = randomGroupInfo(GroupMembershipLevel.JOINED)

        val m = GroupEventMessage.Part(groupInfo.id)

        val processor = createProcessor()

        whenever(groupService.isUserMemberOf(groupInfo.id, sender)).thenResolve(false)

        whenever(groupService.getInfo(m.id)).thenResolve(groupInfo)

        processor.processMessage(sender, wrap(m)).get()

        verify(groupService, never()).removeMember(any(), any())
    }

    @Test
    fun `it should ignore group joins for blocked groups`() {
        val sender = UserId(1)
        val newMember = UserId(2)

        val groupId = randomGroupId()
        val groupInfo = randomGroupInfo(GroupMembershipLevel.BLOCKED)

        val m = GroupEventMessage.Join(groupId, newMember)

        val processor = createProcessor()

        whenever(groupService.getInfo(m.id)).thenResolve(groupInfo)

        processor.processMessage(sender, wrap(m)).get()

        verify(groupService, never()).addMembers(any(), any())
    }

    @Test
    fun `it should ignore group parts for blocked groups`() {
        val sender = randomUserId()
        val groupInfo = randomGroupInfo(GroupMembershipLevel.BLOCKED)

        val m = GroupEventMessage.Part(groupInfo.id)

        val processor = createProcessor()

        whenever(groupService.getInfo(m.id)).thenResolve(groupInfo)

        processor.processMessage(sender, wrap(m)).get()

        verify(groupService, never()).removeMember(any(), any())
    }

    @Test
    fun `it should fetch remote contact info when receiving a group join`() {
        val sender = UserId(1)
        val newMember = UserId(2)
        val groupInfo = randomGroupInfo(GroupMembershipLevel.JOINED)

        val m = GroupEventMessage.Join(groupInfo.id, newMember)

        val processor = createProcessor()

        returnGroupInfo(groupInfo)

        whenever(groupService.addMembers(groupInfo.id, setOf(newMember))).thenResolve(Unit)
        whenever(contactsService.addMissingContacts(any())).thenResolve(emptySet())

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

        whenever(contactsService.addMissingContacts(any())).thenResolve(setOf(newMember))

        processor.processMessage(sender, wrap(m)).get()

        verify(groupService, never()).addMembers(any(), any())
    }

    @Test
    fun `it should store received group text messages to the proper group`() {
        val sender = randomUserId()

        val groupInfo = randomGroupInfo(GroupMembershipLevel.JOINED)
        val m = randomTextMessage(groupInfo.id)

        val processor = createProcessor()

        returnGroupInfo(groupInfo)

        processor.processMessage(sender, wrap(m)).get()

        verify(messageService).addMessage(eq(groupInfo.id.toConversationId()), capture { groupMessageInfo ->
            val messageInfo = groupMessageInfo.info
            assertFalse(messageInfo.isSent, "Message marked as sent")
            assertFalse(messageInfo.isRead, "Message should not be marked as read")
            assertEquals(m.message, messageInfo.message, "Invalid message")
        })
    }

    @Test
    fun `it should mark new messages for the currently group convo focused page as read`() {
        val sender = randomUserId()

        val groupInfo = randomGroupInfo(GroupMembershipLevel.JOINED)
        val m = randomTextMessage(groupInfo.id)

        val processor = createProcessor()

        returnGroupInfo(groupInfo)

        uiEvents.onNext(UIEvent.PageChange(PageType.GROUP, groupInfo.id.toString()))

        processor.processMessage(sender, wrap(m)).get()

        verify(messageService).addMessage(eq(groupInfo.id.toConversationId()), capture {
            assertTrue(it.info.isRead, "Message should be marked as read")
        })
    }

    fun testDropGroupTextMessage(senderIsMember: Boolean, membershipLevel: GroupMembershipLevel) {
        val sender = randomUserId()

        val groupInfo = randomGroupInfo(membershipLevel)
        val m = randomTextMessage(groupInfo.id)

        val processor = createProcessor()

        returnGroupInfo(groupInfo)

        whenever(groupService.isUserMemberOf(groupInfo.id, sender)).thenResolve(senderIsMember)

        processor.processMessage(sender, wrap(m)).get()

        verify(messageService, never()).addMessage(any(), any())
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

    @Test
    fun `it should drop NewDevice messages where the sender is not yourself`() {
        val processor = createProcessor()

        val sender = randomUserId()
        val m = SyncMessage.NewDevice(randomDeviceInfo())

        assertFailsWith(SyncMessageFromOtherSecurityException::class) {
            processor.processMessage(sender, wrap(m)).get()
        }

        verify(messageCipherService, never()).addSelfDevice(any())
    }

    @Test
    fun `it should add a new device when receiving a NewDevice message`() {
        val newDeviceInfo = randomDeviceInfo()

        val processor = createProcessor()

        val sender = selfId
        val m = SyncMessage.NewDevice(newDeviceInfo)

        processor.processMessage(sender, wrap(m)).get()

        verify(messageCipherService).addSelfDevice(newDeviceInfo)
    }

    fun randomSingleSentMessageInfo(userId: UserId): SyncSentMessageInfo {
        return SyncSentMessageInfo(
            randomMessageId(),
            Recipient.User(userId),
            randomMessageText(),
            currentTimestamp(),
            currentTimestamp(),
            randomTtl()
        )
    }

    fun randomGroupSentMessageInfo(groupId: GroupId): SyncSentMessageInfo {
        return SyncSentMessageInfo(
            randomMessageId(),
            Recipient.Group(groupId),
            randomMessageText(),
            currentTimestamp(),
            currentTimestamp(),
            randomTtl()
        )
    }

    @Test
    fun `it should store a new sent single text message when receiving a SelfMessage message for a single chat`() {
        val processor = createProcessor()

        val recipient = randomUserId()
        val sentMessageInfo = randomSingleSentMessageInfo(recipient)
        val messageInfo = sentMessageInfo.toMessageInfo()
        val conversationMessageInfo = ConversationMessageInfo(null, messageInfo)
        val m = SyncMessage.SelfMessage(sentMessageInfo)

        processor.processMessage(selfId, wrap(m)).get()

        verify(messageService).addMessage(recipient.toConversationId(), conversationMessageInfo)
    }

    @Test
    fun `it should call ContactsService to add missing users when receiving a single text message`() {
        val processor = createProcessor()

        val recipient = randomUserId()
        val sentMessageInfo = randomSingleSentMessageInfo(recipient)
        val m = SyncMessage.SelfMessage(sentMessageInfo)

        processor.processMessage(selfId, wrap(m)).get()

        verify(contactsService).addMissingContacts(setOf(recipient))
    }

    @Test
    fun `it should store a new sent group message when receiving a SelfMessage message for a new group chat`() {
        val processor = createProcessor()

        val recipient = randomGroupId()
        val sentMessageInfo = randomGroupSentMessageInfo(recipient)
        val groupMessageInfo = ConversationMessageInfo(null, sentMessageInfo.toMessageInfo())
        val m = SyncMessage.SelfMessage(sentMessageInfo)

        processor.processMessage(selfId, wrap(m)).get()

        verify(messageService).addMessage(recipient.toConversationId(), groupMessageInfo)
    }

    @Test
    fun `it should drop SelfMessage messages where the sender is not yourself`() {
        val processor = createProcessor()

        val sender = randomUserId()
        val m = SyncMessage.SelfMessage(randomSingleSentMessageInfo(randomUserId()))

        assertFailsWith(SyncMessageFromOtherSecurityException::class) {
            processor.processMessage(sender, wrap(m)).get()
        }
    }

    @Test
    fun `it should do an address book pull when receiving an AddressBookSync message`() {
        val processor = createProcessor()

        val sender = selfId
        val m = SyncMessage.AddressBookSync()

        processor.processMessage(sender, wrap(m))

        verify(contactsService).doAddressBookPull()
    }

    @Test
    fun `it should drop AddressBookSync messages where the sender is not yourself`() {
        val processor = createProcessor()

        val sender = randomUserId()
        val m = SyncMessage.AddressBookSync()

        assertFailsWith(SyncMessageFromOtherSecurityException::class) {
            processor.processMessage(sender, wrap(m)).get()
        }

        verify(contactsService, never()).doAddressBookPull()
    }

    @Test
    fun `it should add the sender as an ALL level contact when receiving a WasAdded message`() {
        val processor = createProcessor()

        val sender = randomUserId()

        val m = ControlMessage.WasAdded()

        processor.processMessage(sender, wrap(m))

        verify(contactsService).addById(sender)
    }
}
